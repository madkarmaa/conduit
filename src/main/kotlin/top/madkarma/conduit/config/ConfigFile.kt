package top.madkarma.conduit.config

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.fabricmc.loader.api.FabricLoader

class InvalidConfig(val problems: List<String>) : Exception(problems.joinToString("; "))

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val CONFIG_JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "    "
    encodeDefaults = true
}

object ConfigFile {
    val path: Path = FabricLoader.getInstance().configDir.resolve("conduit.json")

    /**
     * Reads the config, after writing the defaults on first start. Fields the file leaves out keep
     * their defaults. Throws InvalidConfig naming every problem it finds.
     */
    fun read(toolNames: Set<String>): ConduitConfig {
        if (!Files.exists(path)) writeDefaults()

        val defaults = CONFIG_JSON.encodeToJsonElement(ConduitConfig())
        val missing = sortedSetOf<String>()
        val expanded =
            expandVariables(
                merge(defaults, parse(Files.readString(path))),
                System.getenv(),
                missing,
            )
        if (missing.isNotEmpty())
            throw InvalidConfig(listOf("Set the variables ${missing.joinToString(", ")}"))

        val config = decode(expanded)
        val problems = config.problems(toolNames)
        if (problems.isNotEmpty()) throw InvalidConfig(problems)

        return config
    }

    private fun writeDefaults() {
        Files.createDirectories(path.parent)
        Files.writeString(path, CONFIG_JSON.encodeToString(ConduitConfig()) + "\n")
    }

    private fun parse(text: String): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (exception: SerializationException) {
            throw InvalidConfig(listOf("$path is not valid JSON: ${firstLine(exception)}"))
        }

    // Only the first line, because the rest of the message repeats the input, secrets included.
    private fun decode(json: JsonElement): ConduitConfig =
        try {
            CONFIG_JSON.decodeFromJsonElement(json)
        } catch (exception: SerializationException) {
            throw InvalidConfig(listOf(firstLine(exception)))
        } catch (exception: IllegalArgumentException) {
            throw InvalidConfig(listOf(firstLine(exception)))
        }

    private fun firstLine(exception: Exception): String =
        exception.message.orEmpty().lineSequence().first()
}

/** The user's values over the defaults, merging objects field by field. */
private fun merge(defaults: JsonElement, user: JsonElement): JsonElement {
    if (defaults !is JsonObject || user !is JsonObject) return user

    val merged = LinkedHashMap<String, JsonElement>(defaults)
    for ((key, value) in user) merged[key] = defaults[key]?.let { merge(it, value) } ?: value
    return JsonObject(merged)
}
