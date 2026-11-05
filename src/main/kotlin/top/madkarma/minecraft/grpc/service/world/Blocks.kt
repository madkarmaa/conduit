package top.madkarma.minecraft.grpc.service.world

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.storage.TagValueInput
import top.madkarma.minecraft.grpc.game.*
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.notFound
import top.madkarma.minecraft.grpc.proto.BlockPos as ProtoBlockPos

internal fun MinecraftServer.readBlock(request: GetBlockRequest): BlockInfo {
    val level = level(request.dimension)
    val position = request.pos.toMc()

    return blockInfo {
        this.pos = request.pos
        state = level.getBlockState(position).toProto()
        hasBlockEntity = level.getBlockEntity(position) != null
        loaded = level.isLoaded(position)
    }
}

internal fun MinecraftServer.writeBlock(request: SetBlockRequest) {
    val block = parseBlock(registryAccess(), request.state)
    place(level(request.dimension), request.pos.toMc(), block, updateFlags(request.noUpdates))
}

internal fun MinecraftServer.writeBlocks(request: SetBlocksRequest): SetBlocksResponse {
    val level = level(request.dimension)
    val parsed = HashMap<String, ParsedBlock>()

    val changed = request.changesList.count { change ->
        val block = parsed.getOrPut(change.state) { parseBlock(registryAccess(), change.state) }
        place(level, change.pos.toMc(), block, updateFlags(request.noUpdates))
    }

    return setBlocksResponse { this.changed = changed }
}

internal fun MinecraftServer.breakBlock(request: BreakBlockRequest) {
    level(request.dimension).destroyBlock(request.pos.toMc(), request.dropItems)
}

internal fun MinecraftServer.readBlockEntity(request: GetBlockRequest): Nbt {
    val blockEntity = blockEntityAt(level(request.dimension), request.pos)
    return nbt { snbt = blockEntity.saveWithFullMetadata(registryAccess()).toString() }
}

internal fun MinecraftServer.mergeBlockEntity(request: SetBlockEntityRequest) {
    val level = level(request.dimension)
    val blockEntity = blockEntityAt(level, request.pos)

    val merged = blockEntity.saveWithoutMetadata(
        registryAccess(),
    ).merge(TagParser.parseCompoundFully(request.snbt))

    loadBlockEntity(level, blockEntity, merged)
}

internal fun MinecraftServer.place(
    level: ServerLevel,
    position: BlockPos,
    block: ParsedBlock,
    flags: Int,
): Boolean {
    if (!level.isInWorldBounds(
            position,
        )
    ) {
        invalidArgument("Position ${position.x} ${position.y} ${position.z} is outside the world")
    }

    val changed = level.setBlock(position, block.state, flags)
    val nbt = block.nbt ?: return changed

    val blockEntity = level.getBlockEntity(position)
        ?: invalidArgument("${block.state.block} has no block entity data")

    loadBlockEntity(level, blockEntity, nbt)
    return true
}

private fun MinecraftServer.loadBlockEntity(
    level: ServerLevel,
    blockEntity: BlockEntity,
    nbt: CompoundTag,
) {
    blockEntity.loadWithComponents(
        TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), nbt),
    )

    blockEntity.setChanged()

    val state = level.getBlockState(blockEntity.blockPos)
    level.sendBlockUpdated(blockEntity.blockPos, state, state, Block.UPDATE_ALL)
}

private fun blockEntityAt(level: ServerLevel, position: ProtoBlockPos): BlockEntity =
    level.getBlockEntity(position.toMc())
        ?: notFound("No block entity at ${position.x} ${position.y} ${position.z}")

internal fun updateFlags(noUpdates: Boolean): Int = if (noUpdates) {
    Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SKIP_ON_PLACE
} else {
    Block.UPDATE_ALL
}
