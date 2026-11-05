package top.madkarma.minecraft.grpc.service.server

import com.google.protobuf.Empty
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec2
import top.madkarma.minecraft.grpc.game.*
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument

private val EMPTY: Empty = Empty.getDefaultInstance()

private const val OWNER_PERMISSION_LEVEL = 4

class ServerServiceImpl : ServerServiceGrpcKt.ServerServiceCoroutineImplBase() {
    override suspend fun getInfo(request: Empty): ServerInfo =
        GameServer.run { it.describeServer() }

    override suspend fun getPerformance(request: Empty): Performance = GameServer.run {
        it.performance()
    }

    override suspend fun executeCommand(request: ExecuteCommandRequest): ExecuteCommandResponse =
        GameServer.run { server ->
            if (request.command.isBlank()) invalidArgument("Command must not be empty")

            val result = server.runCommand(request.command, server.commandSource(request))

            executeCommandResponse {
                success = result.success
                this.result = result.result
                output += result.output
            }
        }

    override suspend fun broadcast(request: BroadcastRequest): Empty = GameServer.run { server ->
        server.playerList.broadcastSystemMessage(
            request.message.toComponent(server.registryAccess()),
            request.overlay,
        )
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

private fun MinecraftServer.commandSource(request: ExecuteCommandRequest): CommandSourceStack {
    var source = createCommandSourceStack()

    if (request.hasAs()) {
        val entity = entityOrPlayer(request.`as`)

        source = source.withEntity(entity).withPosition(entity.position())
            .withRotation(Vec2(entity.xRot, entity.yRot)).withLevel(entity.level() as ServerLevel)
    }

    if (request.hasDimension()) source = source.withLevel(level(request.dimension))
    if (request.hasPosition()) source = source.withPosition(request.position.toMc())
    if (request.hasRotation()) source = source.withRotation(request.rotation.toVec2())

    val permissionLevel =
        if (request.hasPermissionLevel()) request.permissionLevel else OWNER_PERMISSION_LEVEL

    return source.withPermission(permissionSet(permissionLevel))
}
