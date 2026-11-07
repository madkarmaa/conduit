package top.madkarma.conduit.mcp

import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import top.madkarma.conduit.game.invalidInput

/**
 * Checks arguments against an object schema: types, required fields, enums, number ranges, string
 * lengths and array sizes. Returns the arguments with the defaults of missing fields filled in. A
 * null counts as a missing field.
 */
fun validateArguments(schema: JsonObject, arguments: JsonObject): JsonObject =
    checkObject(schema, arguments, "")

private fun checkObject(schema: JsonObject, value: JsonObject, path: String): JsonObject {
    val properties = schema["properties"] as? JsonObject ?: return value
    val required = (schema["required"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    val checked = LinkedHashMap<String, JsonElement>(value)

    for ((name, field) in properties) {
        val fieldSchema = field as JsonObject
        val fieldPath = if (path.isEmpty()) name else "$path.$name"
        val fieldValue = value[name]?.takeUnless { it is JsonNull }

        checked.remove(name)
        when {
            fieldValue != null -> checked[name] = checkValue(fieldSchema, fieldValue, fieldPath)
            fieldSchema["default"] != null -> checked[name] = fieldSchema.getValue("default")
            name in required -> invalidInput("$fieldPath is required")
        }
    }

    return JsonObject(checked)
}

private fun checkValue(schema: JsonObject, value: JsonElement, path: String): JsonElement {
    val type = (schema["type"] as? JsonPrimitive)?.content
    if (!hasType(value, type)) invalidInput("$path must be ${article(type)} $type")

    checkEnum(schema, value, path)
    return when (value) {
        is JsonObject -> checkObject(schema, value, path)
        is JsonArray -> checkArray(schema, value, path)
        else -> value.also { checkPrimitive(schema, it as JsonPrimitive, path) }
    }
}

private fun hasType(value: JsonElement, type: String?): Boolean {
    val primitive = value as? JsonPrimitive
    val number = primitive?.takeUnless { it.isString }?.doubleOrNull

    return when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "string" -> primitive?.isString == true
        "number" -> number != null
        "integer" -> number != null && number == floor(number)
        "boolean" -> primitive?.takeUnless { it.isString }?.booleanOrNull != null
        else -> true
    }
}

private fun checkEnum(schema: JsonObject, value: JsonElement, path: String) {
    val allowed = schema["enum"] as? JsonArray ?: return
    if (value in allowed) return

    invalidInput("$path must be one of ${allowed.joinToString(", ") { it.jsonPrimitive.content }}")
}

private fun checkArray(schema: JsonObject, value: JsonArray, path: String): JsonArray {
    limit(schema, "minItems")?.let {
        if (value.size < it) invalidInput("$path needs at least $it items")
    }
    limit(schema, "maxItems")?.let {
        if (value.size > it) invalidInput("$path takes at most $it items")
    }

    val items = schema["items"] as? JsonObject ?: return value
    return JsonArray(value.mapIndexed { index, item -> checkValue(items, item, "$path[$index]") })
}

private fun checkPrimitive(schema: JsonObject, value: JsonPrimitive, path: String) {
    if (value.isString) {
        val length = value.content.codePointCount(0, value.content.length)
        limit(schema, "maxLength")?.let {
            if (length > it) invalidInput("$path takes at most $it characters")
        }
        return
    }

    val number = value.doubleOrNull ?: return
    bound(schema, "minimum")?.let {
        if (number < it) invalidInput("$path must be at least ${format(it)}")
    }
    bound(schema, "maximum")?.let {
        if (number > it) invalidInput("$path must be at most ${format(it)}")
    }
}

private fun limit(schema: JsonObject, key: String): Int? = schema[key]?.jsonPrimitive?.int

private fun bound(schema: JsonObject, key: String): Double? =
    schema[key]?.jsonPrimitive?.doubleOrNull

private fun article(type: String?): String =
    if (type == "object" || type == "integer" || type == "array") "an" else "a"

private fun format(number: Double): String =
    if (number == floor(number)) number.toLong().toString() else number.toString()
