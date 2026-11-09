package top.madkarma.conduit.game

import java.util.UUID
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/** An online player by UUID, by name in any case, or by the start of a name only they have. */
fun MinecraftServer.player(ref: String): ServerPlayer {
    if (ref.isBlank()) invalidInput("A player name or UUID is required")

    val byUuid = runCatching { UUID.fromString(ref) }.getOrNull()?.let { playerList.getPlayer(it) }
    return byUuid
        ?: playerList.getPlayerByName(ref)
        ?: playerByPrefix(ref)
        ?: notFound("Player not online: $ref")
}

// Players often call others by the start of their name, such as "alex" for "Alexandra".
private fun MinecraftServer.playerByPrefix(prefix: String): ServerPlayer? {
    val name =
        uniqueNameMatch(playerList.players.map { it.gameProfile.name }, prefix) ?: return null
    return playerList.getPlayerByName(name)
}

/**
 * The one name that starts with prefix, ignoring case, or null when none does. Several matches
 * fail, since picking one could change the wrong player.
 */
fun uniqueNameMatch(names: Collection<String>, prefix: String): String? {
    val matches = names.filter { it.startsWith(prefix, ignoreCase = true) }.distinct().sorted()
    if (matches.size > 1) {
        invalidInput("\"$prefix\" matches several players: ${matches.joinToString()}")
    }
    return matches.singleOrNull()
}
