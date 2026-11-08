package top.madkarma.conduit.mcp

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import top.madkarma.conduit.game.GAME_JSON
import top.madkarma.conduit.game.Vector3

/** What a tool returns: text for the model, and the same data as JSON when there is any. */
class Reply(val text: String, val data: JsonObject? = null)

/** Shows a value as compact JSON, which keeps the model's context small. */
inline fun <reified T> json(value: T): Reply {
    val data = compact(GAME_JSON.encodeToJsonElement(value)) ?: JsonObject(emptyMap())
    return Reply(data.toString(), data as? JsonObject)
}

fun done(text: String): Reply = Reply(text)

/** Says when undo cannot revert an edit, because it changed more blocks than undo keeps. */
fun Reply.noteUndo(undoable: Boolean): Reply =
    if (undoable) this else Reply("$text\nThis change is too large to undo.", data)

fun lines(header: String, rows: List<String>, data: JsonObject): Reply =
    Reply((listOf(header) + rows).joinToString("\n"), data)

/**
 * Drops nulls, empty strings, empty lists and empty objects, and writes whole numbers without a
 * fraction. Returns null when nothing is left.
 */
fun compact(value: JsonElement): JsonElement? =
    when (value) {
        is JsonObject ->
            value
                .mapNotNull { (key, field) -> compact(field)?.let { key to it } }
                .toMap()
                .takeIf { it.isNotEmpty() }
                ?.let(::JsonObject)

        is JsonArray -> value.mapNotNull(::compact).takeIf { it.isNotEmpty() }?.let(::JsonArray)

        JsonNull -> null

        is JsonPrimitive -> compactPrimitive(value)
    }

private fun compactPrimitive(value: JsonPrimitive): JsonPrimitive? {
    if (value.isString) return value.takeIf { it.content.isNotEmpty() }

    val number = value.doubleOrNull ?: return value
    val whole = number == floor(number) && abs(number) < 1e15
    return if (whole && !value.content.all { it.isDigit() || it == '-' })
        JsonPrimitive(number.toLong())
    else value
}

fun position(value: Vector3?): String {
    if (value == null) return "?"
    return listOf(value.x, value.y, value.z).joinToString(" ") { round(it) }
}

/** Rounds to two decimals and leaves out a zero fraction, like 12.5 or 64. */
fun round(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

fun dataOf(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(mapOf(*fields))

/** A value as compact JSON, for the data of a reply. */
inline fun <reified T> encoded(value: T): JsonElement =
    compact(GAME_JSON.encodeToJsonElement(value)) ?: JsonArray(emptyList())

inline fun <reified T> encodedObject(value: T): JsonObject =
    encoded(value) as? JsonObject ?: JsonObject(emptyMap())
