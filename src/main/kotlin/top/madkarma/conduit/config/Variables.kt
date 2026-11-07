package top.madkarma.conduit.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ${NAME} or ${NAME:-default}.
private val REFERENCE = Regex("""\$\{(\w+)(?::-([^}]*))?}""")

/**
 * Replaces the variable references in every string of a JSON value. A reference without a default
 * to a variable that is not set adds the variable's name to missing.
 */
fun expandVariables(
    value: JsonElement,
    environment: Map<String, String>,
    missing: MutableSet<String>,
): JsonElement =
    when {
        value is JsonObject ->
            JsonObject(value.mapValues { expandVariables(it.value, environment, missing) })

        value is JsonArray -> JsonArray(value.map { expandVariables(it, environment, missing) })

        value is JsonPrimitive && value.isString ->
            JsonPrimitive(expand(value.content, environment, missing))

        else -> value
    }

private fun expand(text: String, environment: Map<String, String>, missing: MutableSet<String>) =
    REFERENCE.replace(text) { match ->
        val name = match.groupValues[1]
        val fallback = match.groups[2]?.value

        environment[name] ?: fallback ?: "".also { missing += name }
    }
