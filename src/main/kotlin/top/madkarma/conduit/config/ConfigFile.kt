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
import org.slf4j.LoggerFactory

class InvalidConfig(val problems: List<String>) : Exception(problems.joinToString("; "))

private val CONFIG_JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "    "
    encodeDefaults = true
}

private val LOGGER = LoggerFactory.getLogger("conduit")

object ConfigFile {
    val path: Path = FabricLoader.getInstance().configDir.resolve("conduit.json")

    /**
     * Reads the config, after writing the defaults on first start. Fields the file leaves out keep
     * their defaults. Throws InvalidConfig naming every problem it finds.
     */
    fun read(tools: Map<String, Set<String>>): ConduitConfig {
        if (!Files.exists(path)) writeDefaults()

        val defaults = CONFIG_JSON.encodeToJsonElement(ConduitConfig())
        val missing = mutableListOf<MissingVariable>()
        val merged = merge(defaults, parse(Files.readString(path)))
        val config = decode(expandVariables(merged, System.getenv(), missing))

        // A disabled agent needs no secret, so its variables may stay unset.
        val unset = missing.filter { config.uses(it.path) }.map { it.name }.toSortedSet()
        val variables =
            listOfNotNull(
                unset
                    .takeIf { it.isNotEmpty() }
                    ?.let { "Set the variables ${it.joinToString(", ")}" },
            )
        val problems = variables + config.problems(tools)
        if (problems.isNotEmpty()) throw InvalidConfig(problems)

        warnAboutSubscriptions(config)
        return config
    }

    // The flag is easy to forget once it is on, so every load repeats the warning.
    private fun warnAboutSubscriptions(config: ConduitConfig) {
        val agents = mapOf("full" to config.agents.full, "chat" to config.agents.chat)
        for ((mode, agent) in agents) {
            if (!agent.enabled || !agent.dangerouslyUseSubscriptionCredentials) continue
            LOGGER.warn(
                "agents.{} uses the subscription its CLI is logged in to. The providers' terms " +
                    "forbid this, and it can get the account banned.",
                mode,
            )
        }
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

    // This keeps only an error's first line, because the rest repeats the input, secrets included.
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
