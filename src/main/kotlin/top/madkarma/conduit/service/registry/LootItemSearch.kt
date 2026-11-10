package top.madkarma.conduit.service.registry

import kotlinx.serialization.json.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

/**
 * Whether a loot entry gives an item id, or an item of a #tag. Tag entries count when the item is
 * in their tag.
 */
internal fun itemMatcher(input: String): (JsonObject) -> Boolean {
    if (input.startsWith("#")) {
        val tagId = requireId(input.removePrefix("#"), "item tag")
        val tag = TagKey.create(Registries.ITEM, tagId)
        return { entry ->
            when (entry.text("type")) {
                "minecraft:item" -> itemIn(entry.text("name"), tag)
                "minecraft:tag" -> entry.text("name") == tagId.toString()
                else -> false
            }
        }
    }

    val id = requireId(input, "item").toString()
    return { entry ->
        when (entry.text("type")) {
            "minecraft:item" -> entry.text("name") == id
            "minecraft:tag" -> {
                val tagId = entry.text("name")?.let { requireId(it, "item tag") }
                tagId != null && itemIn(id, TagKey.create(Registries.ITEM, tagId))
            }
            else -> false
        }
    }
}

private fun itemIn(id: String?, tag: TagKey<Item>): Boolean {
    val item = id?.let { BuiltInRegistries.ITEM.getOptional(requireId(it, "item")) } ?: return false
    return item.map { it.builtInRegistryHolder().`is`(tag) }.orElse(false)
}
