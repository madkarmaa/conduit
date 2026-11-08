package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.*

enum class Shape {
    SPHERE,
    CYLINDER,
    BOX,
    WALLS,
    LINE,
}

/**
 * A shape to fill. Spheres and cylinders grow around center, and a cylinder rises height blocks
 * along its axis. Boxes, walls and lines go from one corner to the other.
 */
class ShapeRequest(
    val shape: Shape,
    val center: BlockCoordinates? = null,
    val radius: Int = 0,
    val height: Int = 1,
    val axis: Direction.Axis = Direction.Axis.Y,
    val from: BlockCoordinates? = null,
    val to: BlockCoordinates? = null,
    /** Only the outer layer: a sphere's shell, a cylinder's side or a box's six faces. */
    val hollow: Boolean = false,
)

/** Fills a shape like fill, and returns how many blocks changed. */
internal fun MinecraftServer.fillShape(
    dimension: String?,
    request: ShapeRequest,
    state: String,
    noUpdates: Boolean,
    replace: String?,
): Int {
    val level = level(dimension)
    val block = parseBlock(registryAccess(), state)
    val replaces = replace?.let { blockFilter(it) }

    return shapePositions(request).count { position ->
        if (replaces != null && !replaces(level.getBlockState(position))) return@count false
        place(level, position, block, updateFlags(noUpdates))
    }
}

private fun shapePositions(request: ShapeRequest): Sequence<BlockPos> =
    when (request.shape) {
        Shape.SPHERE -> sphere(required(request.center, "center"), request.radius, request.hollow)
        Shape.CYLINDER -> cylinder(required(request.center, "center"), request)
        Shape.BOX -> box(required(request.from, "from"), required(request.to, "to"), request.hollow)
        Shape.WALLS -> walls(required(request.from, "from"), required(request.to, "to"))
        Shape.LINE -> line(required(request.from, "from"), required(request.to, "to"))
    }

internal fun <T> required(value: T?, name: String): T = value ?: invalidInput("Missing $name")
