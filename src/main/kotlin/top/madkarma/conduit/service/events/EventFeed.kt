package top.madkarma.conduit.service.events

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.events.EventBus
import top.madkarma.conduit.events.EventType
import top.madkarma.conduit.events.GameEvent

private const val BUFFER_SIZE = 1000

val FEED_TYPES: List<EventType> = EventType.entries

data class BufferedEvent(val cursor: Long, val event: GameEvent)

class EventQuery(
    val after: Long,
    /** Event type ids, or every type when empty. */
    val types: Set<String>,
    /** Names or UUIDs of the players or entities the events are about, or anyone when empty. */
    val players: List<String>,
    val limit: Int,
    val timeoutMs: Long,
)

class EventPage(
    val events: List<BufferedEvent>,
    val nextCursor: Long,
    /** Events newer than the query's cursor that left the buffer before they were read. */
    val expired: Long,
)

/** Keeps the latest events and numbers them, so agents can poll with a cursor. */
object EventFeed {
    private val buffer = ArrayDeque<BufferedEvent>()
    private var lastCursor = 0L
    private val newest = MutableStateFlow(0L)
    private val listening = AtomicBoolean(false)

    /**
     * Waits up to the query's timeout for a matching event. Events are kept from the first poll on.
     */
    suspend fun poll(query: EventQuery): EventPage {
        if (listening.compareAndSet(false, true)) EventBus.listen(::add)

        withTimeoutOrNull(query.timeoutMs.milliseconds) {
            newest.first { matching(query).isNotEmpty() }
        }
        return page(query)
    }

    private fun add(event: GameEvent) {
        if (event.type !in FEED_TYPES) return

        val cursor =
            synchronized(buffer) {
                buffer.addLast(BufferedEvent(++lastCursor, event))
                if (buffer.size > BUFFER_SIZE) buffer.removeFirst()
                lastCursor
            }
        newest.value = cursor
    }

    private fun matching(query: EventQuery): List<BufferedEvent> =
        synchronized(buffer) { buffer.filter { matches(it, query) } }

    private fun page(query: EventQuery): EventPage =
        synchronized(buffer) {
            val events = matching(query).take(query.limit)
            val oldest = buffer.firstOrNull()?.cursor ?: (lastCursor + 1)

            EventPage(
                events,
                events.lastOrNull()?.cursor ?: lastCursor,
                max(0, oldest - query.after - 1),
            )
        }

    private fun matches(buffered: BufferedEvent, query: EventQuery): Boolean {
        if (buffered.cursor <= query.after) return false
        if (query.types.isNotEmpty() && buffered.event.type.id !in query.types) return false
        return query.players.isEmpty() || involves(buffered.event.payload, query.players)
    }

    private fun involves(payload: JsonObject, players: List<String>): Boolean {
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
