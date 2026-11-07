package top.madkarma.conduit.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ${NAME} or ${NAME:-default}.
private val REFERENCE = Regex("""\$\{(\w+)(?::-([^}]*))?}""")

/** A variable without a default that is not set, and the config field that uses it. */
class MissingVariable(val name: String, val path: String)

/**
 * Replaces the variable references in every string of a JSON value. A reference to a variable that
 * is not set and has no default becomes an empty string and is added to missing.
 */
fun expandVariables(
    value: JsonElement,
    environment: Map<String, String>,
    missing: MutableList<MissingVariable>,
    path: String = "",
): JsonElement =
    when (value) {
        is JsonObject ->
            JsonObject(
                value.mapValues { (key, field) ->
                    val fieldPath = if (path.isEmpty()) key else "$path.$key"
                    expandVariables(field, environment, missing, fieldPath)
                },
            )

        is JsonArray ->
            JsonArray(
                value.mapIndexed { index, item ->
                    expandVariables(item, environment, missing, "$path[$index]")
                },
            )

        is JsonPrimitive if value.isString ->
            JsonPrimitive(
                expand(value.content, environment) { missing += MissingVariable(it, path) },
            )

        else -> value
    }

private fun expand(text: String, environment: Map<String, String>, onMissing: (String) -> Unit) =
    REFERENCE.replace(text) { match ->
        val name = match.groupValues[1]
        environment[name] ?: match.groups[2]?.value ?: "".also { onMissing(name) }
    }
