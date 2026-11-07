package top.madkarma.conduit.game

import java.util.*
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.entity.Entity
import top.madkarma.conduit.rpc.call

object GameServer {
    @Volatile var current: MinecraftServer? = null

    fun require(): MinecraftServer =
        current ?: unavailable("No Minecraft server is running (open a world first)")

    suspend fun <T> run(block: (MinecraftServer) -> T): T {
        val server = require()
        return server.call { block(server) }
    }
}

fun MinecraftServer.level(dimension: String): ServerLevel =
    getLevel(dimensionKey(dimension)) ?: notFound("Unknown dimension: $dimension")

fun MinecraftServer.player(ref: String): ServerPlayer {
    if (ref.isBlank()) invalidInput("A player name or UUID is required")

    val byUuid = runCatching { UUID.fromString(ref) }.getOrNull()?.let { playerList.getPlayer(it) }
    return byUuid ?: playerList.getPlayerByName(ref) ?: notFound("Player not online: $ref")
}

fun MinecraftServer.entity(uuid: String): Entity {
    val id =
        runCatching { UUID.fromString(uuid) }.getOrNull() ?: invalidInput("Invalid UUID: $uuid")

    for (level in allLevels) {
        level.getEntity(id)?.let {
            return it
        }
    }

    notFound("Entity not found: $uuid")
}

fun MinecraftServer.entityOrPlayer(ref: String): Entity {
    val uuid = runCatching { UUID.fromString(ref) }.getOrNull()
    return if (uuid != null) entity(ref) else player(ref)
}

fun permissionSet(level: Int): LevelBasedPermissionSet {
    if (level !in 0..4) invalidInput("Permission level must be between 0 and 4")
    return LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level))
}

data class CommandResult(val success: Boolean, val result: Int, val output: List<String>)

private class CapturingSource : CommandSource {
    val output = mutableListOf<String>()

    override fun sendSystemMessage(message: Component) {
        output += message.string
    }

    override fun acceptsSuccess() = true

    override fun acceptsFailure() = true

    override fun shouldInformAdmins() = false
}

fun MinecraftServer.runCommand(
    command: String,
    base: CommandSourceStack = createCommandSourceStack(),
): CommandResult {
    val capture = CapturingSource()
    var success = false
    var result = 0
    val source =
        base.withSource(capture).withCallback { ok, value ->
            success = ok
            result = value
        }

    commands.performPrefixedCommand(source, command)
    return CommandResult(success, result, capture.output.toList())
}

fun MinecraftServer.runCommandOrFail(command: String): CommandResult {
    val result = runCommand(command, createCommandSourceStack().withSuppressedOutput())

    if (!result.success) {
        invalidInput(
            "Command failed: /$command: ${result.output.joinToString("; ")}",
        )
    }

    return result
}
