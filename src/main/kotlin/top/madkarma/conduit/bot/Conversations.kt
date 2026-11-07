package top.madkarma.conduit.bot

import java.nio.file.Files
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import top.madkarma.conduit.config.Memory

private val PATH = FabricLoader.getInstance().configDir.resolve("conduit-conversations.json")
private val JSON = Json { ignoreUnknownKeys = true }

/** Where a request runs in the agent's container, and whether it continues an earlier one. */
class Conversation(val folder: String, val resumable: Boolean)

/**
 * The agent CLIs keep a conversation per working directory, so each conversation gets its own
 * folder. Forgetting moves a conversation to a folder with a higher generation, which the CLIs have
 * never seen.
 */
object Conversations {
    @Serializable
    private data class Generations(val base: Int = 0, val keys: Map<String, Int> = emptyMap())

    private var generations: Generations? = null

    /** Chat mode never shares a conversation, with other players or with full mode. */
    @Synchronized
    fun of(mode: Mode, memory: Memory, asker: Asker): Conversation {
        val key =
            when {
                mode == Mode.CHAT -> chatKey(asker.uuid)
                memory == Memory.SHARED -> SHARED_KEY
                memory == Memory.PLAYER -> asker.uuid.toString()
                else -> return Conversation("once-${UUID.randomUUID()}", resumable = false)
            }

        val stored = load()
        return Conversation(
            "$key-${stored.base + stored.keys.getOrDefault(key, 0)}",
            resumable = true,
        )
    }

    /** Starts fresh conversations for a player, in both modes. */
    fun forgetPlayer(uuid: UUID) = forget(listOf(uuid.toString(), chatKey(uuid)))

    fun forgetShared() = forget(listOf(SHARED_KEY))

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

    private fun chatKey(uuid: UUID) = "chat-$uuid"

    private const val SHARED_KEY = "shared"
}
