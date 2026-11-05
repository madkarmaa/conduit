package top.madkarma.minecraft.grpc.service.events

import kotlinx.coroutines.flow.Flow
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
            for (event in subscription.channel) emit(event)
        } finally {
            EventBus.unsubscribe(subscription)
        }
    }
}
