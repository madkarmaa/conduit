package top.madkarma.conduit.events

import java.util.concurrent.CopyOnWriteArrayList
import top.madkarma.conduit.proto.Event
import top.madkarma.conduit.proto.EventType

object EventBus {
    private val subscriptions = CopyOnWriteArrayList<EventSubscription>()

    fun subscribe(subscription: EventSubscription) {
        subscriptions += subscription
    }

    fun unsubscribe(subscription: EventSubscription) {
        subscriptions -= subscription
        subscription.close()
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
}
