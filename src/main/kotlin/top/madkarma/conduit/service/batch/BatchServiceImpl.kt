package top.madkarma.conduit.service.batch

import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.proto.BatchRequest
import top.madkarma.conduit.proto.BatchResponse
import top.madkarma.conduit.proto.BatchServiceGrpcKt

class BatchServiceImpl : BatchServiceGrpcKt.BatchServiceCoroutineImplBase() {
    override suspend fun execute(request: BatchRequest): BatchResponse = GameServer.run {
        it.executeBatch(request)
    }
}
