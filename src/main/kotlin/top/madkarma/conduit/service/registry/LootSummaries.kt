package top.madkarma.conduit.service.registry

import com.mojang.serialization.JsonOps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.LootTable

// Entries that hold other entries, which roll like the entries next to them for a summary.
private val COMPOSITE_ENTRIES =
    setOf("minecraft:alternatives", "minecraft:group", "minecraft:sequence")

/** A loot table in short: one line per pool, with each entry's chance per roll. */
@Serializable data class LootSummary(val id: String, val pools: List<String>)

/**
 * The loot tables whose id contains the filter, in short. With an item id or #tag, only the tables
 * whose own entries can give it, without looking into the tables they name.
 */
internal fun MinecraftServer.lootSummaries(filter: String, item: String?): List<LootSummary> {
    val registries = reloadableRegistries().lookup()
    val context = registries.createSerializationContext(JsonOps.INSTANCE)
    val gives = item?.let(::itemMatcher)

    return registries
        .lookupOrThrow(Registries.LOOT_TABLE)
        .listElements()
        .toList()
        .map { it.key().identifier().toString() to it.value() }
        .filter { (id, _) -> id.contains(filter, ignoreCase = true) }
        .sortedBy { it.first }
        .mapNotNull { (id, table) ->
            val encoded = LootTable.DIRECT_CODEC.encodeStart(context, table).getOrThrow()
            val pools = Json.parseToJsonElement(encoded.toString()).jsonObject.list("pools")
            val entries = pools.flatMap { flatten(it.jsonObject.list("entries")) }
            if (gives != null && entries.none(gives)) return@mapNotNull null

            LootSummary(id, pools.map { describePool(it.jsonObject) })
        }
}

/** A pool as its rolls and its entries, such as "2-6 rolls: diamond 2-7 5%, nothing 95%". */
internal fun describePool(pool: JsonObject): String {
    val entries = flatten(pool.list("entries"))
    val rolls = amount(pool["rolls"])
    val chance = randomChance(pool)?.let { " at ${percent(it)}" }.orEmpty()
    val totalWeight = entries.sumOf { weight(it) }
    val described =
        entries.joinToString(", ") { entry ->
            "${describeEntry(entry)} ${percent(weight(entry) / totalWeight)}"
        }
    val noun = if (rolls == "1") "roll" else "rolls"
    return "$rolls $noun$chance: $described"
}

private fun describeEntry(entry: JsonObject): String {
    val name = entry.text("name")?.removePrefix("minecraft:")
    val base =
        when (entry.text("type")) {
            "minecraft:item" -> name ?: "?"
            "minecraft:tag" -> "#$name"
            "minecraft:loot_table" -> "table ${entry.text("value") ?: "inline"}"
            "minecraft:empty" -> "nothing"
            else -> entry.text("type") ?: "?"
        }
    return base + modifiers(entry).mapNotNull(::describeModifier).joinToString("")
}

private fun describeModifier(modifier: JsonObject): String? =
    when (modifier.text("type")) {
        "minecraft:set_count" -> " ${amount(modifier["count"])}"
        "minecraft:enchant_with_levels" -> " enchanted ${amount(modifier["levels"])} levels"
        "minecraft:enchant_randomly" -> " randomly enchanted"
        else -> null
    }

// Groups and alternatives become their children, so their items count too.
private fun flatten(entries: List<JsonElement>): List<JsonObject> = entries.flatMap { element ->
    val entry = element.jsonObject
    if (entry.text("type") in COMPOSITE_ENTRIES) flatten(entry.list("children")) else listOf(entry)
}
