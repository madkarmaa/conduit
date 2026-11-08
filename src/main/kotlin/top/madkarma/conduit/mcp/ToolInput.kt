package top.madkarma.conduit.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import top.madkarma.conduit.game.invalidInput

@PublishedApi internal val INPUT_JSON = Json { ignoreUnknownKeys = true }

/**
 * Arguments of a tool call that already passed validation, with defaults filled in. Fields only
 * some actions need are optional in the schema, so each action names its own with require. The
 * policy is the caller's, for actions whose answer depends on who asks.
 */
class ToolInput(val arguments: JsonObject, val policy: ToolPolicy) {
    fun has(name: String): Boolean = arguments[name] != null

    fun require(vararg names: String) {
        val missing = names.filterNot(::has)
        if (missing.isNotEmpty()) invalidInput("Missing ${missing.joinToString(", ")}")
    }

    fun stringOrNull(name: String): String? = primitive(name)?.content

    fun string(name: String): String = stringOrNull(name) ?: missing(name)

    fun longOrNull(name: String): Long? =
        primitive(name)?.let { it.longOrNull ?: it.double.toLong() }

    fun long(name: String): Long = longOrNull(name) ?: missing(name)

    fun intOrNull(name: String): Int? = longOrNull(name)?.toInt()

    fun int(name: String): Int = long(name).toInt()

    fun doubleOrNull(name: String): Double? = primitive(name)?.double

    fun double(name: String): Double = doubleOrNull(name) ?: missing(name)

    fun floatOrNull(name: String): Float? = doubleOrNull(name)?.toFloat()

    fun float(name: String): Float = double(name).toFloat()

    fun booleanOrNull(name: String): Boolean? = primitive(name)?.boolean

    fun boolean(name: String): Boolean = booleanOrNull(name) ?: missing(name)

    fun strings(name: String): List<String> =
        arguments[name]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    inline fun <reified T> decodeOrNull(name: String): T? =
        arguments[name]?.let { INPUT_JSON.decodeFromJsonElement<T>(it) }

    inline fun <reified T> decode(name: String): T = decodeOrNull(name) ?: missing(name)

    private fun primitive(name: String): JsonPrimitive? = arguments[name]?.jsonPrimitive

    @PublishedApi internal fun missing(name: String): Nothing = invalidInput("Missing $name")
}
