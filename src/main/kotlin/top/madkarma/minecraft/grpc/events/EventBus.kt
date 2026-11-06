package top.madkarma.minecraft.grpc.events

import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import net.minecraft.world.entity.player.Player
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.game.parseId
import top.madkarma.minecraft.grpc.proto.Event
import top.madkarma.minecraft.grpc.proto.EventType
import top.madkarma.minecraft.grpc.proto.SubscribeRequest

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

    // Slow clients lose the oldest events instead of stalling the game thread.
    val channel = Channel<Event>(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun wantsPlayer(player: Player): Boolean {
        return players.isEmpty() ||
            player.stringUUID in players ||
            player.gameProfile.name.lowercase() in players
    }

    fun wantsEntityType(type: String): Boolean {
        if (entityTypes.isEmpty()) return type !in EXPLICIT_ENTITY_TYPES
        return type in entityTypes
    }

    fun send(event: Event) {
        channel.trySend(event)
    }
}

private val SUBSCRIBABLE: Set<EventType> =
    EventType.entries.toSet() - EventType.EVENT_TYPE_UNSPECIFIED - EventType.UNRECOGNIZED

// High-volume types have to be requested explicitly.
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

object EventBus {
    private val subscriptions = CopyOnWriteArrayList<EventSubscription>()

    fun subscribe(subscription: EventSubscription) {
        subscriptions += subscription
    }

    fun unsubscribe(subscription: EventSubscription) {
        subscriptions -= subscription
        subscription.channel.close()
    }

    fun wants(type: EventType): Boolean = subscriptions.any { type in it.types }

    fun subscribers(type: EventType): List<EventSubscription> = subscriptions.filter {
        type in it.types
    }

    // Inline so that events nobody receives are never built. [accepts] applies per-subscription
    // filters, so one event can go to some subscriptions and not others.
    inline fun publish(
        type: EventType,
        accepts: (EventSubscription) -> Boolean = { true },
        fill: Event.Builder.() -> Unit,
    ) {
        if (!wants(type)) return

        val receivers = subscribers(type).filter(accepts)
        if (receivers.isEmpty()) return

        val event = buildEvent(fill)
        for (subscription in receivers) subscription.send(event)
    }

    inline fun buildEvent(fill: Event.Builder.() -> Unit): Event {
        val builder =
            Event.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setTick(GameServer.current?.tickCount ?: 0)

        builder.fill()
        return builder.build()
    }
}
