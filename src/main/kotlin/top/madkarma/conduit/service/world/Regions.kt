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

        if (registryAccess().lookupOrThrow(Registries.BLOCK).get(tag).isEmpty) {
            notFound("Unknown block tag: $input")
        }

        return { it.`is`(tag) }
    }

    val target = parseBlock(registryAccess(), input).state
    return if ('[' in input) ({ it == target }) else ({ it.block == target.block })
}

internal fun bounds(from: BlockCoordinates, to: BlockCoordinates): Pair<BlockPos, BlockPos> {
    val min = BlockPos(min(from.x, to.x), min(from.y, to.y), min(from.z, to.z))
    val max = BlockPos(max(from.x, to.x), max(from.y, to.y), max(from.z, to.z))
    val volume = (max.x - min.x + 1L) * (max.y - min.y + 1L) * (max.z - min.z + 1L)

    if (volume > MAX_REGION_VOLUME) {
        invalidInput("Region of $volume blocks exceeds the limit of $MAX_REGION_VOLUME")
    }

    return min to max
}
