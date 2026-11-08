package top.madkarma.conduit.service.world

import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.ToolInput

/** Where find searches, and the point it measures distances from. */
class BlockArea(val from: BlockCoordinates, val to: BlockCoordinates, val center: BlockPos) {
    val min = BlockPos(min(from.x, to.x), min(from.y, to.y), min(from.z, to.z))
    val max = BlockPos(max(from.x, to.x), max(from.y, to.y), max(from.z, to.z))

    val chunkCount: Long
        get() = ((max.x shr 4) - (min.x shr 4) + 1L) * ((max.z shr 4) - (min.z shr 4) + 1L)
}

/** A cuboid from from and to searches around its middle, and a radius around its center. */
internal fun searchArea(input: ToolInput): BlockArea {
    val radius = input.intOrNull("radius")
    if (radius != null) {
        val center = input.decode<BlockCoordinates>("center")
        val from = BlockCoordinates(center.x - radius, center.y - radius, center.z - radius)
        val to = BlockCoordinates(center.x + radius, center.y + radius, center.z + radius)
        return BlockArea(from, to, center.toMc())
    }

    if (!input.has("from") || !input.has("to")) {
        invalidInput("Give from and to, or center and radius")
    }

    val from = input.decode<BlockCoordinates>("from")
    val to = input.decode<BlockCoordinates>("to")
    val middle = BlockPos((from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2)
    return BlockArea(from, to, middle)
}
