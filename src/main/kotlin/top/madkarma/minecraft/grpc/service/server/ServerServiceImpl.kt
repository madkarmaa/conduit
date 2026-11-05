package top.madkarma.minecraft.grpc.service.server

import com.google.protobuf.Empty
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.*

private val EMPTY: Empty = Empty.getDefaultInstance()

class ServerServiceImpl : ServerServiceGrpcKt.ServerServiceCoroutineImplBase() {
    override suspend fun getInfo(request: Empty): ServerInfo =
        GameServer.run { it.describeServer() }

    override suspend fun getPerformance(request: Empty): Performance = GameServer.run {
        it.performance()
    }

    override suspend fun executeCommand(request: ExecuteCommandRequest): ExecuteCommandResponse =
        GameServer.run { it.executeCommand(request) }

    override suspend fun broadcast(request: BroadcastRequest): Empty = GameServer.run {
        it.broadcast(request)
        EMPTY
    }

    override suspend fun save(request: SaveRequest): Empty = GameServer.run { server ->
        server.saveEverything(false, request.flush, true)
        EMPTY
    }

    /** Schedules the shutdown instead of running it inline, so the call can still complete. */
    override suspend fun stop(request: Empty): Empty {
        val server = GameServer.require()
        server.execute { server.halt(false) }
        return EMPTY
    }
}
