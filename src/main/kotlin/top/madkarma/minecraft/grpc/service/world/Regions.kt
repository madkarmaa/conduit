package top.madkarma.minecraft.grpc.service.world

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import top.madkarma.minecraft.grpc.game.level
import top.madkarma.minecraft.grpc.game.parseBlock
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import kotlin.math.max
import kotlin.math.min
import top.madkarma.minecraft.grpc.proto.BlockPos as ProtoBlockPos

private const val MAX_REGION_VOLUME = 1_000_000L

internal fun MinecraftServer.readRegion(request: GetBlocksRequest): BlockRegion {
    val level = level(request.dimension)
    val (min, max) = bounds(request.from, request.to)
    val palette = LinkedHashMap<BlockState, Int>()
    val indices = ArrayList<Int>()
    val voidAir = Blocks.VOID_AIR.defaultBlockState()
    val cursor = BlockPos.MutableBlockPos()

    for (y in min.y..max.y) {
        for (z in min.z..max.z) {
            for (x in min.x..max.x) {
                cursor.set(x, y, z)

                val unloaded = request.loadedOnly && !level.isLoaded(cursor)
                val state = if (unloaded) voidAir else level.getBlockState(cursor)

                indices += palette.getOrPut(state) { palette.size }
            }
        }
    }

    return blockRegion {
        this.min = min.toProto()
        size = BlockPos(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1).toProto()
        this.palette += palette.keys.map { it.toProto() }
        blocks += indices
    }
}

internal fun MinecraftServer.fillRegion(request: FillRequest): SetBlocksResponse {
    val level = level(request.dimension)
    val (min, max) = bounds(request.from, request.to)
    val block = parseBlock(registryAccess(), request.state)
    val replaces = if (request.hasReplace()) replaceFilter(request.replace) else null
    var changed = 0

    for (position in BlockPos.betweenClosed(min, max)) {
        if (replaces != null && !replaces(level.getBlockState(position))) continue
        if (place(level, position.immutable(), block, updateFlags(request.noUpdates))) changed++
    }

    return setBlocksResponse { this.changed = changed }
}

// A bare block id ("minecraft:furnace") is meant to match all of its states.
private fun MinecraftServer.replaceFilter(input: String): (BlockState) -> Boolean {
    val target = parseBlock(registryAccess(), input).state
    return if ('[' in input) ({ it == target }) else ({ it.block == target.block })
}

private fun bounds(from: ProtoBlockPos, to: ProtoBlockPos): Pair<BlockPos, BlockPos> {
    val min = BlockPos(min(from.x, to.x), min(from.y, to.y), min(from.z, to.z))
    val max = BlockPos(max(from.x, to.x), max(from.y, to.y), max(from.z, to.z))
    val volume = (max.x - min.x + 1L) * (max.y - min.y + 1L) * (max.z - min.z + 1L)

    if (volume > MAX_REGION_VOLUME) {
        invalidArgument("Region of $volume blocks exceeds the limit of $MAX_REGION_VOLUME")
    }

    return min to max
}
