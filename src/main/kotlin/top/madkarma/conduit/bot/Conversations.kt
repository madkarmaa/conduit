package top.madkarma.conduit.bot

import java.nio.file.Files
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader

private val PATH = FabricLoader.getInstance().configDir.resolve("conduit-conversations.json")
private val JSON = Json { ignoreUnknownKeys = true }

/**
 * The agent CLIs keep a conversation per working directory, so each conversation gets its own
 * folder. Each player has one conversation per mode, and each trigger one of its own. Forgetting
 * moves a conversation to a folder with a higher generation, which the CLIs have never seen.
 */
object Conversations {
    @Serializable
    private data class Generations(val base: Int = 0, val keys: Map<String, Int> = emptyMap())

    private var generations: Generations? = null

    // Folders whose last request was cancelled. A restart forgets them, which only loses a hint.
    private val cancelled = mutableSetOf<String>()

    /** The folder of the asker's conversation in a mode. */
    @Synchronized
    fun of(mode: Mode, asker: Asker): String =
        folder(if (mode == Mode.CHAT) chatKey(asker.uuid) else asker.uuid.toString())

    @Synchronized fun ofTrigger(name: String): String = folder("trigger-$name")

    private fun folder(key: String): String {
        val stored = load()
        return "$key-${stored.base + stored.keys.getOrDefault(key, 0)}"
    }

    /** Starts fresh conversations for a player, in both modes. */
    fun forgetPlayer(uuid: UUID) = forget(listOf(uuid.toString(), chatKey(uuid)))

    @Synchronized
    fun forgetAll() {
        save(load().let { it.copy(base = it.base + 1) })
    }

    @Synchronized
    private fun forget(keys: List<String>) {
        val stored = load()
        save(
            stored.copy(
                keys = stored.keys + keys.associateWith { stored.keys.getOrDefault(it, 0) + 1 },
            ),
        )
    }

    private fun load(): Generations =
        generations
            ?: (if (Files.exists(PATH)) JSON.decodeFromString(Files.readString(PATH))
                else Generations())
                .also {
                    generations = it
                }

    private fun save(updated: Generations) {
        generations = updated
        Files.writeString(PATH, JSON.encodeToString(updated))
    }

    /** Remembers that the last request of a conversation was cancelled. */
    @Synchronized
    fun markCancelled(folder: String) {
        cancelled += folder
    }

    /**
     * Whether the last request of a conversation was cancelled. Forgets it, since it is told once.
     */
    @Synchronized fun takeCancelled(folder: String): Boolean = cancelled.remove(folder)

    private fun chatKey(uuid: UUID) = "chat-$uuid"
}
