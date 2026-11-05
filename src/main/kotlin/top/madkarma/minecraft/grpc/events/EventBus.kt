package top.madkarma.minecraft.grpc.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.Event
import top.madkarma.minecraft.grpc.proto.EventType
import top.madkarma.minecraft.grpc.proto.SubscribeRequest
import java.util.concurrent.CopyOnWriteArrayList

class EventSubscription(request: SubscribeRequest) {
    val types: Set<EventType> =
        request.typesList.filter { it in SUBSCRIBABLE }.toSet().ifEmpty { DEFAULT_TYPES }

    val tickInterval = if (request.tickInterval > 0) request.tickInterval else 20

    // Slow clients lose the oldest events instead of stalling the game thread.
    val channel = Channel<Event>(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun send(event: Event) {
        channel.trySend(event)
    }
}

private val SUBSCRIBABLE: Set<EventType> =
    EventType.entries.toSet() - EventType.EVENT_TYPE_UNSPECIFIED - EventType.UNRECOGNIZED

// The server tick fires 20 times per second, so it has to be requested explicitly.
private val DEFAULT_TYPES: Set<EventType> = SUBSCRIBABLE - EventType.EVENT_TYPE_SERVER_TICK

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

    fun subscribers(type: EventType): List<EventSubscription> =
        subscriptions.filter { type in it.types }

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
        val builder = Event.newBuilder().setTimestampMs(System.currentTimeMillis())
            .setTick(GameServer.current?.tickCount ?: 0)

        builder.fill()
        return builder.build()
    }
}
