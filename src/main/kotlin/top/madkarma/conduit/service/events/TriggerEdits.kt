package top.madkarma.conduit.service.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.config.CONFIG_JSON
import top.madkarma.conduit.config.TriggerConfig
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.notFound

/** The triggers of the loaded config. */
internal fun currentTriggers(): List<TriggerConfig> = Conduit.config?.triggers.orEmpty()

/**
 * Changes the config file, like /conduit access does, and loads it. A change that makes the config
 * invalid leaves the file and the loaded config as they were.
 */
internal fun changeConfig(change: (JsonObject) -> JsonObject) {
    val problems = Conduit.updateConfig(change)
    if (problems.isEmpty()) return

    invalidInput(
        "Nothing changed, since the config would be invalid: ${problems.joinToString("; ")}",
    )
}

/** Adds a trigger to the JSON of the config file. */
internal fun JsonObject.addingTrigger(trigger: TriggerConfig): JsonObject =
    withTriggers { triggers ->
        if (triggers.any { it.name() == trigger.name }) {
            invalidInput("A trigger named ${trigger.name} exists. Remove it or pick another name")
        }
        triggers + CONFIG_JSON.encodeToJsonElement(trigger).jsonObject
    }

internal fun JsonObject.removingTrigger(name: String): JsonObject = withTriggers { triggers ->
    if (triggers.none { it.name() == name }) noTrigger(name, triggers)
    triggers.filterNot { it.name() == name }
}

internal fun JsonObject.enablingTrigger(name: String, enabled: Boolean): JsonObject =
    withTriggers { triggers ->
        if (triggers.none { it.name() == name }) noTrigger(name, triggers)
        triggers.map { trigger ->
            if (trigger.name() != name) trigger
            else JsonObject(trigger + ("enabled" to JsonPrimitive(enabled)))
        }
    }

// Works on the triggers as the file holds them, so the other triggers keep their variables.
private fun JsonObject.withTriggers(change: (List<JsonObject>) -> List<JsonObject>): JsonObject {
    val triggers = this["triggers"]?.jsonArray.orEmpty().map { it.jsonObject }
    return JsonObject(this + ("triggers" to JsonArray(change(triggers))))
}

private fun JsonObject.name(): String? = (this["name"] as? JsonPrimitive)?.content

private fun noTrigger(name: String, triggers: List<JsonObject>): Nothing {
    val names = triggers.mapNotNull { it.name() }.joinToString(", ")
    notFound(if (names.isEmpty()) "There are no triggers" else "No trigger is named $name: $names")
}
