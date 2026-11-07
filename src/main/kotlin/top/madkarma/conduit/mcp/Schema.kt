package top.madkarma.conduit.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The JSON Schema of a tool's input object. */
class InputSchema(val properties: Map<String, JsonObject>, val required: List<String>) {
    fun toJson(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
    }
}

class InputBuilder {
    private val properties = LinkedHashMap<String, JsonObject>()
    private val required = mutableListOf<String>()

    fun required(name: String, schema: JsonObject) {
        properties[name] = schema
        required += name
    }

    fun optional(name: String, schema: JsonObject) {
        properties[name] = schema
    }

    fun build() = InputSchema(properties, required)
}

fun input(build: InputBuilder.() -> Unit): InputSchema = InputBuilder().apply(build).build()

fun objectSchema(description: String? = null, build: InputBuilder.() -> Unit): JsonObject {
    val fields = input(build).toJson()
    return if (description == null) fields else fields.described(description)
}

fun stringSchema(description: String? = null, default: String? = null, maxLength: Int? = null) =
    schema("string", description) {
        default?.let { put("default", it) }
        maxLength?.let { put("maxLength", it) }
    }

fun integerSchema(
    description: String? = null,
    min: Int? = null,
    max: Int? = null,
    default: Int? = null,
) =
    schema("integer", description) {
        min?.let { put("minimum", it) }
        max?.let { put("maximum", it) }
        default?.let { put("default", it) }
    }

fun numberSchema(
    description: String? = null,
    min: Double? = null,
    max: Double? = null,
    default: Double? = null,
) =
    schema("number", description) {
        min?.let { put("minimum", it) }
        max?.let { put("maximum", it) }
        default?.let { put("default", it) }
    }

fun booleanSchema(description: String? = null, default: Boolean? = null) =
    schema("boolean", description) { default?.let { put("default", it) } }

fun enumSchema(values: List<String>, description: String? = null, default: String? = null) =
    schema("string", description) {
        put("enum", JsonArray(values.map(::JsonPrimitive)))
        default?.let { put("default", it) }
    }

fun arraySchema(
    items: JsonObject,
    description: String? = null,
    minItems: Int? = null,
    maxItems: Int? = null,
    default: JsonArray? = null,
) =
    schema("array", description) {
        put("items", items)
        minItems?.let { put("minItems", it) }
        maxItems?.let { put("maxItems", it) }
        default?.let { put("default", it) }
    }

/** The same schema with another description, like zod's describe. */
fun JsonObject.described(description: String): JsonObject =
    JsonObject(this + ("description" to JsonPrimitive(description)))

private fun schema(type: String, description: String?, extra: JsonObjectBuilder.() -> Unit) =
    buildJsonObject {
        put("type", type)
        extra()
        description?.let { put("description", it) }
    }

val EMPTY_ARRAY = JsonArray(emptyList<JsonElement>())
