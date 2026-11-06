package top.madkarma.minecraft.grpc.service.server

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec2
import top.madkarma.minecraft.grpc.game.*
import top.madkarma.minecraft.grpc.proto.BroadcastRequest
import top.madkarma.minecraft.grpc.proto.ExecuteCommandRequest
import top.madkarma.minecraft.grpc.proto.ExecuteCommandResponse
import top.madkarma.minecraft.grpc.proto.executeCommandResponse
import top.madkarma.minecraft.grpc.rpc.invalidArgument

private const val OWNER_PERMISSION_LEVEL = 4

internal fun MinecraftServer.executeCommand(
    request: ExecuteCommandRequest,
): ExecuteCommandResponse {
    if (request.command.isBlank()) invalidArgument("Command must not be empty")

    val result = runCommand(request.command, commandSource(request))

    return executeCommandResponse {
        success = result.success
        this.result = result.result
        output += result.output
    }
}

internal fun MinecraftServer.broadcast(request: BroadcastRequest) {
    playerList.broadcastSystemMessage(
        request.message.toComponent(registryAccess()),
        request.overlay,
    )
}

private fun MinecraftServer.commandSource(request: ExecuteCommandRequest): CommandSourceStack {
    var source = createCommandSourceStack()

    if (request.hasAs()) {
        val entity = entityOrPlayer(request.`as`)

        source =
            source
                .withEntity(entity)
                .withPosition(entity.position())
                .withRotation(Vec2(entity.xRot, entity.yRot))
                .withLevel(entity.level() as ServerLevel)
    }

    if (request.hasDimension()) source = source.withLevel(level(request.dimension))
    if (request.hasPosition()) source = source.withPosition(request.position.toMc())
    if (request.hasRotation()) source = source.withRotation(request.rotation.toVec2())

    val permissionLevel =
        if (request.hasPermissionLevel()) request.permissionLevel else OWNER_PERMISSION_LEVEL

    return source.withPermission(permissionSet(permissionLevel))
}
