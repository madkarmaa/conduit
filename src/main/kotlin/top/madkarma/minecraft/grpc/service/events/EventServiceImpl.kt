package top.madkarma.minecraft.grpc.service.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import top.madkarma.minecraft.grpc.events.EventBus
import top.madkarma.minecraft.grpc.events.EventSubscription
import top.madkarma.minecraft.grpc.proto.Event
import top.madkarma.minecraft.grpc.proto.EventServiceGrpcKt
import top.madkarma.minecraft.grpc.proto.SubscribeRequest

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
