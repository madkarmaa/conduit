package top.madkarma.conduit.service.world

import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.MapColor
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.level
import top.madkarma.conduit.mcp.Pixels

private const val MAX_COLUMNS = 256
private const val TARGET_PIXELS = 512
private const val MAX_SCALE = 16
private const val LEGEND_SIZE = 8

/** A top view, the most common surface blocks with their colors, and how it was drawn. */
class RegionImage(
    val pixels: Pixels,
    val scale: Int,
    val legend: List<Pair<String, Int>>,
)

/**
 * Draws each column of a cuboid from above, as the highest block that is not air between the
 * cuboid's top and bottom. A cuboid one block tall draws that layer. North is up, like on maps.
 */
internal fun MinecraftServer.drawRegionImage(
    dimension: String?,
    from: BlockCoordinates,
    to: BlockCoordinates,
    loadedOnly: Boolean,
): RegionImage {
    val level = level(dimension)
    val min = BlockPos(min(from.x, to.x), max(min(from.y, to.y), level.minY), min(from.z, to.z))
    val max = BlockPos(max(from.x, to.x), min(max(from.y, to.y), level.maxY), max(from.z, to.z))
    val width = max.x - min.x + 1
    val depth = max.z - min.z + 1
    if (width > MAX_COLUMNS || depth > MAX_COLUMNS) {
        invalidInput("image draws up to $MAX_COLUMNS by $MAX_COLUMNS columns")
    }

    val columns =
        Array(depth) { z ->
            Array(width) { x -> topBlock(level, min.x + x, min.z + z, min.y, max.y, loadedOnly) }
        }
    val colors = IntArray(width * depth)
    val counts = HashMap<String, Int>()
    val legendColors = HashMap<String, Int>()

    for (z in 0 until depth) for (x in 0 until width) {
        val top = columns[z][x] ?: continue
        val color = shade(level, top, columns.getOrNull(z - 1)?.get(x))
        colors[z * width + x] = color

        val id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(top).block).toString()
        counts.merge(id, 1, Int::plus)
        legendColors.putIfAbsent(id, color)
    }

    val scale = (TARGET_PIXELS / max(width, depth)).coerceIn(1, MAX_SCALE)
    val legend =
        counts.entries
            .sortedByDescending { it.value }
            .take(LEGEND_SIZE)
            .map { it.key to legendColors.getValue(it.key) }
    return RegionImage(scaled(colors, width, depth, scale), scale, legend)
}

private fun topBlock(
    level: ServerLevel,
    x: Int,
    z: Int,
    minY: Int,
    maxY: Int,
    loadedOnly: Boolean,
): BlockPos? {
    if (loadedOnly && !level.hasChunk(x shr 4, z shr 4)) return null

    // The heightmap skips the air above the surface. It loads the chunk first, like terrain does.
    level.getChunk(x shr 4, z shr 4)
    val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1
    val pos = BlockPos.MutableBlockPos(x, min(surface, maxY), z)

    while (pos.y >= minY) {
        if (!level.getBlockState(pos).isAir) return pos.immutable()
        pos.move(0, -1, 0)
    }
    return null
}

// Like a map, a block higher than the one north of it is lighter, and a lower one darker.
private fun shade(level: ServerLevel, top: BlockPos, north: BlockPos?): Int {
    val brightness =
        when {
            north == null || north.y == top.y -> MapColor.Brightness.NORMAL
            top.y > north.y -> MapColor.Brightness.HIGH
            else -> MapColor.Brightness.LOW
        }
    return level.getBlockState(top).getMapColor(level, top).calculateARGBColor(brightness)
}

private fun scaled(colors: IntArray, width: Int, depth: Int, scale: Int): Pixels {
    val pixels = IntArray(width * scale * depth * scale)
    for (y in 0 until depth * scale) for (x in 0 until width * scale) {
        pixels[y * width * scale + x] = colors[(y / scale) * width + x / scale]
    }
    return Pixels(width * scale, depth * scale, pixels)
}
