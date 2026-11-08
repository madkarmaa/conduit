package top.madkarma.conduit.service.world

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.Reply

const val MAX_WATCHED_BLOCKS = 64
const val MAX_WATCH_TICKS = 200

private const val POLL_INTERVAL_MS = 10L
private const val TICK_MS = 50L
// A frozen or lagging game ticks slower, so the watch ends after this much more time.
private const val EXTRA_WAIT_MS = 5000L

private class Reading(val tick: Int, val states: List<String>)

/**
 * Reads the blocks after every game tick, and lists their first states and each change, such as a
 * redstone lamp that turns on.
 */
suspend fun watchBlocks(dimension: String?, positions: List<BlockCoordinates>, ticks: Int): Reply {
    val first = GameServer.run { it.read(dimension, positions) }
    val changes = mutableListOf<String>()
    val endsAt = System.currentTimeMillis() + ticks * TICK_MS + EXTRA_WAIT_MS
    var last = first

    while (last.tick - first.tick < ticks && System.currentTimeMillis() < endsAt) {
        delay(POLL_INTERVAL_MS.milliseconds)
        val reading = GameServer.run { it.read(dimension, positions) }
        if (reading.tick == last.tick) continue

        reading.states.forEachIndexed { index, state ->
            if (state != last.states[index]) {
                changes += "tick ${reading.tick - first.tick}: ${positions[index].shown()} $state"
            }
        }
        last = reading
    }

    val start = positions.indices.map { "${positions[it].shown()} ${first.states[it]}" }
    val lines =
        listOf(
            "Watched ${positions.size} blocks for ${last.tick - first.tick} ticks. At the start:",
        ) +
            start +
            (if (changes.isEmpty()) listOf("No block changed.") else listOf("Changes:") + changes)
    return Reply(lines.joinToString("\n"))
}

private fun MinecraftServer.read(dimension: String?, positions: List<BlockCoordinates>): Reading {
    val level = level(dimension)
    val states = positions.map { position ->
        val pos = position.toMc()
        if (!level.isLoaded(pos)) failedPrecondition("${position.shown()} is not loaded")
        BlockStateParser.serialize(level.getBlockState(pos))
    }
    return Reading(tickCount, states)
}

private fun BlockCoordinates.shown() = "$x $y $z"
