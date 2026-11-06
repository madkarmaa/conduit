package top.madkarma.conduit.service.server

import com.google.protobuf.Empty
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.proto.*

private val EMPTY: Empty = Empty.getDefaultInstance()

class ServerServiceImpl : ServerServiceGrpcKt.ServerServiceCoroutineImplBase() {
    override suspend fun getInfo(request: Empty): ServerInfo = GameServer.run {
        it.describeServer()
    }

    override suspend fun getPerformance(request: Empty): Performance = GameServer.run {
        it.performance()
    }

    override suspend fun executeCommand(request: ExecuteCommandRequest): ExecuteCommandResponse =
        GameServer.run {
            it.executeCommand(request)
        }

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

    override suspend fun listDatapacks(request: Empty): DatapackList = GameServer.run {
        it.listDatapacks()
    }

    override suspend fun setDatapackEnabled(request: SetDatapackEnabledRequest): DatapackList =
        changeDatapackEnabled(request)

    override suspend fun backup(request: BackupRequest): BackupResponse = createBackup(request)

    override suspend fun listBackups(request: Empty): BackupList = describeBackups()
}
