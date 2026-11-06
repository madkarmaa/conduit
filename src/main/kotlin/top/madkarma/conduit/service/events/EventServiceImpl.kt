package top.madkarma.conduit.service.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import top.madkarma.conduit.events.EventBus
import top.madkarma.conduit.events.EventSubscription
import top.madkarma.conduit.proto.Event
import top.madkarma.conduit.proto.EventServiceGrpcKt
import top.madkarma.conduit.proto.SubscribeRequest

class EventServiceImpl : EventServiceGrpcKt.EventServiceCoroutineImplBase() {
    override fun subscribe(request: SubscribeRequest): Flow<Event> = flow {
        val subscription = EventSubscription(request)

        EventBus.subscribe(subscription)

        try {
            emitAll(subscription.events())
        } finally {
            EventBus.unsubscribe(subscription)
        }
    }
}
