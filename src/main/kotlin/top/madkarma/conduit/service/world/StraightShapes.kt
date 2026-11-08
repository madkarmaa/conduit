package top.madkarma.conduit.service.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import net.minecraft.core.BlockPos
import top.madkarma.conduit.game.*

internal fun box(
    from: BlockCoordinates,
    to: BlockCoordinates,
    hollow: Boolean,
): Sequence<BlockPos> {
    val (min, max) = bounds(from, to)
    return cuboid(from, to).filter {
        !hollow ||
            it.x == min.x ||
            it.x == max.x ||
            it.y == min.y ||
            it.y == max.y ||
            it.z == min.z ||
            it.z == max.z
    }
}

internal fun walls(from: BlockCoordinates, to: BlockCoordinates): Sequence<BlockPos> {
    val (min, max) = bounds(from, to)
    return cuboid(from, to).filter {
        it.x == min.x || it.x == max.x || it.z == min.z || it.z == max.z
    }
}

internal fun line(from: BlockCoordinates, to: BlockCoordinates): Sequence<BlockPos> {
    val steps = max(abs(to.x - from.x), max(abs(to.y - from.y), abs(to.z - from.z)))
    if (steps == 0) return sequenceOf(from.toMc())
    if (steps >= MAX_REGION_VOLUME) invalidInput("A line may have up to $MAX_REGION_VOLUME blocks")

    return (0..steps).asSequence().map { step ->
        val part = step.toDouble() / steps
        BlockPos(
            from.x + ((to.x - from.x) * part).roundToInt(),
            from.y + ((to.y - from.y) * part).roundToInt(),
            from.z + ((to.z - from.z) * part).roundToInt(),
        )
    }
}

// bounds() checks the region limit, so every shape stays within it.
internal fun cuboid(from: BlockCoordinates, to: BlockCoordinates): Sequence<BlockPos> {
    val (min, max) = bounds(from, to)
    return BlockPos.betweenClosed(min, max).asSequence().map { it.immutable() }
}
