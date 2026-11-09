package top.madkarma.conduit.service.world

import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import top.madkarma.conduit.game.*

internal const val MAX_REGION_VOLUME = 1_000_000L

/** A cuboid region as a palette and indices into it, in x, then z, then y order. */
@Serializable
data class BlockRegion(
    val min: BlockCoordinates,
    val size: BlockCoordinates,
    val palette: List<BlockStateInfo>,
    val blocks: List<Int>,
)

/** Reads up to 1,000,000 blocks. With loadedOnly, unloaded positions read as void air. */
internal fun MinecraftServer.readRegion(
    dimension: String?,
    from: BlockCoordinates,
    to: BlockCoordinates,
    loadedOnly: Boolean,
): BlockRegion {
    val level = level(dimension)
    val (min, max) = bounds(from, to)
    val palette = LinkedHashMap<BlockState, Int>()
    val indices = ArrayList<Int>()
    val voidAir = Blocks.VOID_AIR.defaultBlockState()
    val cursor = BlockPos.MutableBlockPos()

    for (y in min.y..max.y) {
        for (z in min.z..max.z) {
            for (x in min.x..max.x) {
                cursor.set(x, y, z)

                val unloaded = loadedOnly && !level.isLoaded(cursor)
                val state = if (unloaded) voidAir else level.getBlockState(cursor)

                indices += palette.getOrPut(state) { palette.size }
            }
        }
    }

    return BlockRegion(
        min = min.toCoordinates(),
        size = BlockCoordinates(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1),
        palette = palette.keys.map { it.toInfo() },
        blocks = indices,
    )
}

/**
 * Fills a cuboid, corners inclusive, and returns how many blocks changed. With replace, it only
 * replaces blocks that match that block id or state.
 */
internal fun MinecraftServer.fillRegion(
    dimension: String?,
    from: BlockCoordinates,
    to: BlockCoordinates,
    state: String,
    noUpdates: Boolean,
    replace: String?,
): Int {
    val level = level(dimension)
    val (min, max) = bounds(from, to)
    val block = parseBlock(registryAccess(), state)
    val replaces = replace?.let { blockFilter(it) }
    var changed = 0

    for (position in BlockPos.betweenClosed(min, max)) {
        if (replaces != null && !replaces(level.getBlockState(position))) continue
        if (place(level, position.immutable(), block, updateFlags(noUpdates))) changed++
    }

    return changed
}

/**
 * Matches a block state, a block id such as "minecraft:furnace" with all of its states, or a block
 * tag such as "#minecraft:logs".
 */
internal fun MinecraftServer.blockFilter(input: String): (BlockState) -> Boolean {
    if (input.startsWith("#")) {
        val tag = TagKey.create(Registries.BLOCK, parseId(input.removePrefix("#")))

        val blocks = registryAccess().lookupOrThrow(Registries.BLOCK)
        if (blocks.get(tag).isEmpty) unknownTag("block", input, blocks)

        return { it.`is`(tag) }
    }

    val target = parseBlock(registryAccess(), input).state
    return if ('[' in input) ({ it == target }) else ({ it.block == target.block })
}

internal fun bounds(from: BlockCoordinates, to: BlockCoordinates): Pair<BlockPos, BlockPos> {
    val min = BlockPos(min(from.x, to.x), min(from.y, to.y), min(from.z, to.z))
    val max = BlockPos(max(from.x, to.x), max(from.y, to.y), max(from.z, to.z))
    val sizeX = max.x - min.x + 1L
    val sizeY = max.y - min.y + 1L
    val sizeZ = max.z - min.z + 1L
    if (sizeX * sizeY * sizeZ > MAX_REGION_VOLUME) invalidInput(tooLarge(sizeX, sizeY, sizeZ))

    return min to max
}

// Says how to split the region, so the agent needs one more try instead of several.
private fun tooLarge(sizeX: Long, sizeY: Long, sizeZ: Long): String {
    val volume = sizeX * sizeY * sizeZ
    val problem =
        "Region of $volume blocks (${sizeX}x${sizeY}x$sizeZ) exceeds the limit of $MAX_REGION_VOLUME."
    val fittingHeight = MAX_REGION_VOLUME / (sizeX * sizeZ)
    if (fittingHeight < 1) return "$problem Split it along x or z."

    val parts = (sizeY + fittingHeight - 1) / fittingHeight
    return "$problem Split it into $parts parts of up to $fittingHeight blocks of height."
}
