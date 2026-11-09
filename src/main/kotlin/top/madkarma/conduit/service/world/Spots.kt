package top.madkarma.conduit.service.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.BoundingBox
import top.madkarma.conduit.game.level
import top.madkarma.conduit.mcp.Reply
import top.madkarma.conduit.mcp.ToolInput
import top.madkarma.conduit.mcp.dataOf
import top.madkarma.conduit.mcp.encoded
import top.madkarma.conduit.mcp.lines

private const val SPOT_LIMIT = 5

/** A place where a player can stand, with its distance from the search center. */
@Serializable
data class Spot(
    /** Feet position at the middle of the block, ready for a teleport. */
    val x: Double,
    val y: Int,
    val z: Double,
    val floor: String,
    val distance: Double,
)

internal fun MinecraftServer.spotsReply(input: ToolInput): Reply {
    val outside = input.decodeOrNull<Bounds>("outside")?.toBox()
    val limit = if (input.has("limit")) input.int("limit") else SPOT_LIMIT
    val spots =
        findSpots(
            input.stringOrNull("dimension"),
            searchArea(input),
            outside,
            input.boolean("loaded_only"),
            limit,
        )

    return lines(
        "${spots.size} safe spots, nearest first, as feet positions:",
        spots.map { "${it.x} ${it.y} ${it.z} on ${it.floor}, ${it.distance} away" },
        dataOf("spots" to encoded(spots)),
    )
}

/**
 * The safe spots of an area nearest to its center, leaving out the ones inside `outside`. Shells
 * around the center are searched outward, so a near spot ends the search early.
 */
internal fun MinecraftServer.findSpots(
    dimension: String?,
    area: BlockArea,
    outside: BoundingBox?,
    loadedOnly: Boolean,
    limit: Int,
): List<Spot> {
    val level = level(dimension)
    val (min, max) = bounds(area.from, area.to)
    val center = area.center
    val found = mutableListOf<Spot>()
    val farthestShell =
        maxOf(
            center.x - min.x,
            max.x - center.x,
            center.y - min.y,
            max.y - center.y,
            center.z - min.z,
            max.z - center.z,
        )

    for (shell in 0..farthestShell) {
        // A spot in a later shell is at least that far away, so the nearest ones are known.
        if (found.size >= limit && found.sortedBy { it.distance }[limit - 1].distance <= shell)
            break

        for (pos in shellPositions(center, shell)) {
            if (!pos.isInside(min, max) || outside?.isInside(pos) == true) continue
            if (loadedOnly && !level.isLoaded(pos)) continue
            if (!isSafeSpot(level, pos)) continue

            found += spotAt(pos, center, level.getBlockState(pos.below()).block)
        }
    }

    return found.sortedBy { it.distance }.take(limit)
}

private fun spotAt(pos: BlockPos, center: BlockPos, floor: Block): Spot {
    val distance = sqrt(pos.distSqr(center))
    return Spot(
        pos.x + 0.5,
        pos.y,
        pos.z + 0.5,
        BuiltInRegistries.BLOCK.getKey(floor).toString(),
        Math.round(distance * 10) / 10.0,
    )
}

private fun BlockPos.isInside(min: BlockPos, max: BlockPos): Boolean =
    x in min.x..max.x && y in min.y..max.y && z in min.z..max.z

/** The positions whose largest offset from the center on any axis is the shell's size. */
internal fun shellPositions(center: BlockPos, shell: Int): Sequence<BlockPos> = sequence {
    for (dx in -shell..shell) {
        for (dy in -shell..shell) {
            val onFace = max(abs(dx), abs(dy)) == shell
            val zOffsets = if (onFace) -shell..shell else listOf(-shell, shell).distinct()
            for (dz in zOffsets) yield(center.offset(dx, dy, dz))
        }
    }
}
