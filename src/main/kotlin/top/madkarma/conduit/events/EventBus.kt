package top.madkarma.conduit.events

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import top.madkarma.conduit.game.GAME_JSON
import top.madkarma.conduit.game.GameServer

enum class EventType {
    CHAT,
    PLAYER_JOIN,
    PLAYER_LEAVE,
    PLAYER_RESPAWN,
    BLOCK_BREAK,
    BLOCK_PLACE,
    BLOCK_USE,
    ENTITY_DAMAGE,
    ENTITY_DEATH,
    ENTITY_ATTACK,
    PLAYER_CHANGE_DIMENSION,
    COMMAND,
    SERVER_STARTED,
    SERVER_STOPPING,
    ENTITY_SPAWN,
    ADVANCEMENT,
    INVENTORY_CHANGE,
    ITEM_PICKUP,
    /**
     * Items a player drops or throws, including the items dropped on death and the items that did
     * not fit when giving items.
     */
    ITEM_DROP,
    ITEM_CRAFT,
    ITEM_SMELT,
    TRADE;

    /** The name readers see, such as "player_join". */
    val id: String = name.lowercase()
}

class GameEvent(
    val type: EventType,
    /** Server tick count at the time of the event. */
    val tick: Int,
    val payload: JsonObject,
) {
    /** Whether the event is about one of these players or entities, by name or UUID. */
    fun involves(players: List<String>): Boolean {
        val entity = payload["entity"] as? JsonObject
        val candidates =
            listOf(
                    payload["player"],
                    payload["playerUuid"],
                    entity?.get("name"),
                    entity?.get("uuid"),
                )
                .mapNotNull { (it as? JsonPrimitive)?.content }

        return candidates.any { candidate ->
            players.any { it.equals(candidate, ignoreCase = true) }
        }
    }
}

object EventBus {
    @PublishedApi internal val listeners = CopyOnWriteArrayList<(GameEvent) -> Unit>()

    fun listen(listener: (GameEvent) -> Unit) {
        listeners += listener
    }

    fun stopListening(listener: (GameEvent) -> Unit) {
        listeners -= listener
    }

    fun wants(): Boolean = listeners.isNotEmpty()

    // Inline, so callers never build events that nobody receives.
    inline fun <reified Payload> publish(type: EventType, payload: () -> Payload) {
        if (listeners.isEmpty()) return

        val encoded = GAME_JSON.encodeToJsonElement(payload()).jsonObject
        val tick = GameServer.current?.tickCount ?: 0
        val event = GameEvent(type, tick, encoded)

        for (listener in listeners) listener(event)
    }
}
