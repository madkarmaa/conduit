package top.madkarma.conduit.service.events

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents

private const val DEFAULT_SIZE = 200

@Serializable
data class ChatLine(
    val time: String,
    val player: String,
    val playerUuid: String,
    val message: String,
)

/**
 * Keeps the latest chat messages from the server start. It listens to chat on its own, so other
 * events never push messages out, and no other event is built for it. Commands stay out, since
 * login commands carry passwords and private messages are private.
 */
object ChatHistory {
    /** The most messages it keeps. Zero turns the history off. */
    @Volatile var size: () -> Int = { DEFAULT_SIZE }

    private val lines = ArrayDeque<ChatLine>()

    fun register() {
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            add(
                ChatLine(
                    now(),
                    sender.gameProfile.name,
                    sender.stringUUID,
                    message.signedContent(),
                ),
            )
        }
    }

    @Synchronized
    fun add(line: ChatLine) {
        lines.addLast(line)
        trim()
    }

    /** The latest messages, oldest first, of the given players by name or UUID, or of anyone. */
    @Synchronized
    fun recent(players: List<String>, limit: Int): List<ChatLine> {
        trim()
        val matching = lines.filter { line ->
            players.isEmpty() ||
                players.any {
                    it.equals(line.player, ignoreCase = true) ||
                        it.equals(line.playerUuid, ignoreCase = true)
                }
        }
        return matching.takeLast(limit)
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }

    // A smaller size from a reloaded config drops the oldest messages at once.
    private fun trim() {
        val kept = size().coerceAtLeast(0)
        while (lines.size > kept) lines.removeFirst()
    }

    private fun now(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
}
