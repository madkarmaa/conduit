package top.madkarma.conduit.config

import java.util.UUID

internal fun ConduitConfig.accessProblems(): List<String> {
    val lists =
        mapOf(
            "access.full.allow" to access.full.allow,
            "access.full.deny" to access.full.deny,
            "access.chat.allow" to access.chat.allow,
            "access.chat.deny" to access.chat.deny,
        )

    val invalid = lists.flatMap { (path, entries) ->
        entries.mapIndexedNotNull { index, entry ->
            "$path[$index].uuid must be a UUID".takeIf { !isUuid(entry.uuid) }
        }
    }
    val deniedOwner =
        access.full.deny
            .filter { access.isOwner(it.name, it.uuid) }
            .map { "access.full.deny must not name the owner ${it.name}" }

    return invalid + deniedOwner
}

private fun isUuid(text: String): Boolean = runCatching { UUID.fromString(text) }.isSuccess
