package top.madkarma.minecraft.grpc.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.Event
import top.madkarma.minecraft.grpc.proto.EventType
import java.util.concurrent.CopyOnWriteArrayList

class EventSubscription(types: Collection<EventType>, val tickInterval: Int) {
    val types: Set<EventType> =
        types.filter { it in SUBSCRIBABLE }.toSet().ifEmpty { DEFAULT_TYPES }

    // Slow clients lose the oldest events instead of stalling the game thread.
    val channel = Channel<Event>(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
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

    // Inline so that events nobody subscribed to are never built.
    inline fun publish(type: EventType, fill: Event.Builder.() -> Unit) {
        if (!wants(type)) return
        val builder = Event.newBuilder()
            .setTimestampMs(System.currentTimeMillis())
            .setTick(GameServer.current?.tickCount ?: 0)
        builder.fill()
        deliver(type, builder.build())
    }

    fun deliver(type: EventType, event: Event) {
        for (subscription in subscriptions) {
            if (type !in subscription.types) continue
            val isTick = type == EventType.EVENT_TYPE_SERVER_TICK
            if (isTick && event.tick % subscription.tickInterval != 0) continue
            subscription.channel.trySend(event)
        }
    }
}
