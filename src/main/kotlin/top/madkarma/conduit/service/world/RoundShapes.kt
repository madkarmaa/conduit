package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import top.madkarma.conduit.game.*

internal fun sphere(center: BlockCoordinates, radius: Int, hollow: Boolean): Sequence<BlockPos> {
    val from = BlockCoordinates(center.x - radius, center.y - radius, center.z - radius)
    val to = BlockCoordinates(center.x + radius, center.y + radius, center.z + radius)

    return cuboid(from, to).filter {
        val distance =
            squared(it.x - center.x) + squared(it.y - center.y) + squared(it.z - center.z)
        inRing(distance, radius, hollow)
    }
}

internal fun cylinder(base: BlockCoordinates, request: ShapeRequest): Sequence<BlockPos> {
    val center = base.toMc()
    val radius = request.radius
    val axis = Direction.get(Direction.AxisDirection.POSITIVE, request.axis)
    val across = Direction.Axis.entries.filter { it != request.axis }
    if (request.height < 1) invalidInput("height must be at least 1")

    val near = center.relative(across[0], -radius).relative(across[1], -radius)
    val far = center.relative(across[0], radius).relative(across[1], radius)
    bounds(near.toCoordinates(), far.relative(axis, request.height - 1).toCoordinates())

    return sequence {
        for (u in -radius..radius) for (v in -radius..radius) {
            if (!inRing(squared(u) + squared(v), radius, request.hollow)) continue
            val base = center.relative(across[0], u).relative(across[1], v)
            for (step in 0 until request.height) yield(base.relative(axis, step))
        }
    }
}

// Half a block of slack rounds the edge like a drawn circle. A hollow shape keeps a layer one
// block thick.
private fun inRing(squaredDistance: Int, radius: Int, hollow: Boolean): Boolean {
    val outside = squaredDistance > squared(radius + 0.5)
    val inside = squaredDistance <= squared(radius - 0.5)
    return !outside && !(hollow && inside && radius > 0)
}

private fun squared(value: Int): Int = value * value

private fun squared(value: Double): Double = value * value
