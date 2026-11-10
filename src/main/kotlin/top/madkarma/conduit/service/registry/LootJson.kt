package top.madkarma.conduit.service.registry

import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

// Reads the datapack JSON of loot tables for their summaries.

internal fun weight(entry: JsonObject): Double =
    (entry["weight"] as? JsonPrimitive)?.doubleOrNull ?: 1.0

internal fun randomChance(pool: JsonObject): Double? {
    val conditions = (pool["condition"] ?: pool["conditions"])?.let(::asList).orEmpty()
    val chance = conditions.firstOrNull { it.text("type") == "minecraft:random_chance" }
    return (chance?.get("chance") as? JsonPrimitive)?.doubleOrNull
}

internal fun modifiers(entry: JsonObject): List<JsonObject> =
    (entry["modifier"] ?: entry["modifiers"] ?: entry["functions"])?.let(::asList).orEmpty()

/** A number provider as "2" or "2-7", or "?" for the kinds a summary does not work out. */
internal fun amount(element: JsonElement?): String =
    when (element) {
        is JsonPrimitive -> number(element.doubleOrNull)
        is JsonObject -> {
            val min = (element["min"] as? JsonPrimitive)?.doubleOrNull
            val max = (element["max"] as? JsonPrimitive)?.doubleOrNull
            val value = (element["value"] as? JsonPrimitive)?.doubleOrNull
            if (min != null && max != null) "${number(min)}-${number(max)}" else number(value)
        }
        else -> "?"
    }

internal fun number(value: Double?): String =
    when {
        value == null -> "?"
        value == value.roundToInt().toDouble() -> value.roundToInt().toString()
        else -> "%.1f".format(value)
    }

internal fun percent(share: Double): String {
    val rounded = (share * 100).roundToInt()
    return if (rounded == 0 && share > 0) "<1%" else "$rounded%"
}

internal fun asList(element: JsonElement): List<JsonObject> =
    when (element) {
        is JsonArray -> element.map { it.jsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }

internal fun JsonObject.list(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
