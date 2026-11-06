package top.madkarma.minecraft.grpc.service.batch

import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.BatchRequest
import top.madkarma.minecraft.grpc.proto.BatchResponse
import top.madkarma.minecraft.grpc.proto.BatchServiceGrpcKt

class BatchServiceImpl : BatchServiceGrpcKt.BatchServiceCoroutineImplBase() {
    override suspend fun execute(request: BatchRequest): BatchResponse = GameServer.run {
        it.executeBatch(request)
    }
}
