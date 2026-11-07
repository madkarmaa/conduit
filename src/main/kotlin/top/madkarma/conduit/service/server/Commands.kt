package top.madkarma.conduit.service.server

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec2
import top.madkarma.conduit.game.*

private const val OWNER_PERMISSION_LEVEL = 4

data class CommandRequest(
    /** Command with or without the leading slash. */
    val command: String,
    /** Player name or UUID, or entity UUID to run as. Null runs as the server console. */
    val runAs: String? = null,
    val dimension: String? = null,
    val position: Vector3? = null,
    val rotation: Rotation? = null,
    val permissionLevel: Int = OWNER_PERMISSION_LEVEL,
)

internal fun MinecraftServer.executeCommand(request: CommandRequest): CommandResult {
    if (request.command.isBlank()) invalidInput("Command must not be empty")

    return runCommand(request.command, commandSource(request))
}

internal fun MinecraftServer.broadcast(message: String, overlay: Boolean) {
    playerList.broadcastSystemMessage(textComponent(message, registryAccess()), overlay)
}

private fun MinecraftServer.commandSource(request: CommandRequest): CommandSourceStack {
    var source = createCommandSourceStack()

    if (request.runAs != null) {
        val entity = entityOrPlayer(request.runAs)

        source =
            source
                .withEntity(entity)
                .withPosition(entity.position())
                .withRotation(Vec2(entity.xRot, entity.yRot))
                .withLevel(entity.level() as ServerLevel)
    }

    if (request.dimension != null) source = source.withLevel(level(request.dimension))
    if (request.position != null) source = source.withPosition(request.position.toMc())
    if (request.rotation != null) source = source.withRotation(request.rotation.toVec2())

    return source.withPermission(permissionSet(request.permissionLevel))
}
