package top.madkarma.conduit.game

import com.mojang.brigadier.exceptions.CommandSyntaxException
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.entity.Entity

private const val GAME_THREAD_TIMEOUT_MS = 30_000L

object GameServer {
    @Volatile var current: MinecraftServer? = null

    fun require(): MinecraftServer =
        current ?: unavailable("No Minecraft server is running (open a world first)")

    /** Runs the block on the server thread, the only thread that may touch game state. */
    suspend fun <T> run(block: (MinecraftServer) -> T): T {
        val server = require()

        try {
            if (server.isSameThread) return block(server)

            return withTimeout(GAME_THREAD_TIMEOUT_MS.milliseconds) {
                CompletableFuture.supplyAsync({ block(server) }, server).await()
            }
        } catch (exception: TimeoutCancellationException) {
            throw ConduitError.TimedOut("Game thread did not respond in time")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw exception.asConduitError()
        }
    }
}

/**
 * The game reports bad input and bad state with its own exceptions. This turns them into
 * ConduitErrors and leaves every other exception as it is.
 */
fun Throwable.asConduitError(): Throwable {
    val cause = (this as? CompletionException)?.cause ?: this
    val message = cause.message ?: cause.toString()

    return when (cause) {
        is ConduitError -> cause
        is CommandSyntaxException,
        is IllegalArgumentException -> ConduitError.InvalidInput(message)
        is IllegalStateException -> ConduitError.FailedPrecondition(message)
        else -> cause
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
