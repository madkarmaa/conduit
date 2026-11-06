package top.madkarma.conduit.events

import java.util.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.minecraft.world.entity.player.Player
import top.madkarma.conduit.game.parseId
import top.madkarma.conduit.proto.Event
import top.madkarma.conduit.proto.EventType
import top.madkarma.conduit.proto.SubscribeRequest

class EventSubscription(request: SubscribeRequest) {
    val types: Set<EventType> =
        request.typesList.filter { it in SUBSCRIBABLE }.toSet().ifEmpty { DEFAULT_TYPES }

    val tickInterval = if (request.tickInterval > 0) request.tickInterval else 20

    val moveMinDistance = if (request.moveMinDistance > 0) request.moveMinDistance else 1.0
    val moveMinRotation = request.moveMinRotation
    val moveHeartbeatTicks = request.moveHeartbeatTicks

    // Last move event sent for each player; only touched on the server thread.
    val sentMoves = HashMap<UUID, PlayerMove>()

    private val players: Set<String> = request.playersList.map { it.lowercase() }.toSet()

    private val entityTypes: Set<String> =
        request.entityTypesList.map { parseId(it).toString() }.toSet()

    val includeRecipeAdvancements = request.includeRecipeAdvancements

    // Slow clients lose the oldest events instead of stalling the game thread. Numbering events as
    // they are sent lets the reading side see exactly which ones were lost.
    private val channel =
        Channel<SequencedEvent>(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var nextSequence = 1L

    fun wantsPlayer(player: Player): Boolean {
        return players.isEmpty() ||
            player.stringUUID in players ||
            player.gameProfile.name.lowercase() in players
    }

    fun wantsEntityType(type: String): Boolean {
        if (entityTypes.isEmpty()) return type !in EXPLICIT_ENTITY_TYPES
        return type in entityTypes
    }

    // Synchronized so events published from different threads keep their numbers in channel order.
    @Synchronized
    fun send(event: Event) {
        channel.trySend(SequencedEvent(nextSequence++, event))
    }

    fun close() {
        channel.close()
    }

    // Stamps the sequence number here instead of in send, so the copy is made on the gRPC thread.
    fun events(): Flow<Event> = flow {
        var lastSequence = 0L
        for (sequenced in channel) {
            val missed = sequenced.sequence - lastSequence - 1
            if (missed > 0) emit(droppedEvent(lastSequence + 1, missed))

            emit(sequenced.event.toBuilder().setSequence(sequenced.sequence).build())
            lastSequence = sequenced.sequence
        }
    }
}

private class SequencedEvent(val sequence: Long, val event: Event)

private val SUBSCRIBABLE: Set<EventType> =
    EventType.entries.toSet() - EventType.EVENT_TYPE_UNSPECIFIED - EventType.UNRECOGNIZED

// A subscription only gets the high-volume types when it names them.
private val DEFAULT_TYPES: Set<EventType> =
    SUBSCRIBABLE -
        setOf(
            EventType.EVENT_TYPE_SERVER_TICK,
            EventType.EVENT_TYPE_PLAYER_MOVE,
            EventType.EVENT_TYPE_ENTITY_SPAWN,
            EventType.EVENT_TYPE_ENTITY_REMOVE,
            EventType.EVENT_TYPE_CHUNK_LOAD,
            EventType.EVENT_TYPE_CHUNK_UNLOAD,
            EventType.EVENT_TYPE_INVENTORY_CHANGE,
        )

// Item entities and experience orbs come and go constantly, and player events already cover
// players, so these only show up in entity events when a subscription lists them.
private val EXPLICIT_ENTITY_TYPES =
    setOf("minecraft:item", "minecraft:experience_orb", "minecraft:player")
