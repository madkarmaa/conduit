package top.madkarma.conduit.mcp

import com.networknt.schema.Error
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.path.PathType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import top.madkarma.conduit.game.invalidInput

// The messages are in English whatever the server's locale, they name fields like "from.x", and
// 3.0 counts as an integer like 3.
private val REGISTRY: SchemaRegistry =
    SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { registry ->
        val config =
            SchemaRegistryConfig.builder()
                .locale(Locale.ENGLISH)
                .pathType(PathType.JSON_PATH)
                .losslessNarrowing(true)
                .build()
        registry.schemaRegistryConfig(config)
    }

// Every request builds its tools anew, so this compiles each schema once and keeps it.
private val compiledSchemas = ConcurrentHashMap<JsonObject, Schema>()

/**
 * Checks arguments against a tool's input schema. Returns the arguments with the defaults of
 * missing fields filled in. A null counts as a missing field.
 */
fun validateArguments(schema: JsonObject, arguments: JsonObject): JsonObject {
    val prepared = withDefaults(schema, arguments)
    val compiled = compiledSchemas.getOrPut(schema) { REGISTRY.getSchema(schema.toString()) }
    val errors = compiled.validate(prepared.toString(), InputFormat.JSON)

    if (errors.isNotEmpty()) invalidInput(errors.joinToString("; ", transform = ::describe))
    return prepared
}

// JSON Schema only describes defaults, so filling them in is up to the server.
private fun withDefaults(schema: JsonObject, value: JsonObject): JsonObject {
    val properties = schema["properties"] as? JsonObject ?: return value
    val filled = LinkedHashMap(value.filterValues { it !is JsonNull })

    for ((name, field) in properties) {
        val fieldSchema = field as JsonObject
        val current = filled[name]
        val default = fieldSchema["default"]

        if (current == null && default != null) filled[name] = default
        if (current != null) filled[name] = withNestedDefaults(fieldSchema, current)
    }

    return JsonObject(filled)
}

private fun withNestedDefaults(schema: JsonObject, value: JsonElement): JsonElement {
    val items = schema["items"] as? JsonObject
    return when {
        value is JsonObject -> withDefaults(schema, value)
        value is JsonArray && items != null ->
            JsonArray(value.map { withNestedDefaults(items, it) })
        else -> value
    }
}

// Names the field without the root, such as "from.x: must be an integer".
private fun describe(error: Error): String {
    val location = error.instanceLocation.toString().removePrefix("$").removePrefix(".")
    return if (location.isEmpty()) error.message else "$location: ${error.message}"
}
