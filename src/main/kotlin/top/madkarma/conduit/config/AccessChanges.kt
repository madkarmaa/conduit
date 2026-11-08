package top.madkarma.conduit.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Lets a player into a mode, which also takes them off its deny list. */
fun ModeAccess.allowing(entry: AccessEntry) =
    ModeAccess(allow = allow.without(entry) + entry, deny = deny.without(entry))

/** Keeps a player out of a mode, which also takes them off its allow list. */
fun ModeAccess.denying(entry: AccessEntry) =
    ModeAccess(allow = allow.without(entry), deny = deny.without(entry) + entry)

private fun List<AccessEntry>.without(entry: AccessEntry) = filterNot {
    it.uuid.equals(entry.uuid, ignoreCase = true)
}

/** Changes access.<mode> in the JSON of the config file. */
fun JsonObject.withModeAccess(mode: String, change: (ModeAccess) -> ModeAccess): JsonObject {
    val access = this["access"] as? JsonObject ?: JsonObject(emptyMap())
    val current =
        access[mode]?.let { CONFIG_JSON.decodeFromJsonElement<ModeAccess>(it) } ?: ModeAccess()
    val changed = JsonObject(access + (mode to CONFIG_JSON.encodeToJsonElement(change(current))))
    return JsonObject(this + ("access" to changed))
}
