package top.madkarma.conduit.service.world

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import top.madkarma.conduit.game.*

@Serializable
data class BlockDetails(
    val pos: BlockCoordinates,
    val state: BlockStateInfo,
    val hasBlockEntity: Boolean,
    val loaded: Boolean,
    val biome: String,
    /** SNBT of the block entity, such as a chest or sign, if the block has one. */
    val blockEntity: String?,
)

@Serializable data class BlockChange(val pos: BlockCoordinates, val state: String)

internal fun MinecraftServer.describeBlock(
    dimension: String?,
    pos: BlockCoordinates,
): BlockDetails {
    val level = level(dimension)
    val position = pos.toMc()
    val blockEntity = level.getBlockEntity(position)

    return BlockDetails(
        pos = pos,
        state = level.getBlockState(position).toInfo(),
        hasBlockEntity = blockEntity != null,
        loaded = level.isLoaded(position),
        biome = level.getBiome(position).unwrapKey().getOrNull()?.identifier()?.toString() ?: "",
        blockEntity = blockEntity?.saveWithFullMetadata(registryAccess())?.toString(),
    )
}

/** Places the block states in one tick and returns how many blocks changed. */
internal fun MinecraftServer.writeBlocks(
    dimension: String?,
    changes: List<BlockChange>,
    noUpdates: Boolean,
): Int {
    val level = level(dimension)
    val parsed = HashMap<String, ParsedBlock>()

    return changes.count { change ->
        val block = parsed.getOrPut(change.state) { parseBlock(registryAccess(), change.state) }
        place(level, change.pos.toMc(), block, updateFlags(noUpdates))
    }
}

internal fun MinecraftServer.breakBlock(
    dimension: String?,
    pos: BlockCoordinates,
    dropItems: Boolean,
) {
    val level = level(dimension)
    EditJournal.before(level, pos.toMc())
    level.destroyBlock(pos.toMc(), dropItems)
}

internal fun MinecraftServer.mergeBlockEntity(
    dimension: String?,
    pos: BlockCoordinates,
    snbt: String,
) {
    val level = level(dimension)
    val blockEntity = blockEntityAt(level, pos)
    EditJournal.before(level, blockEntity.blockPos)

    val merged =
        blockEntity.saveWithoutMetadata(registryAccess()).merge(TagParser.parseCompoundFully(snbt))

    level.loadBlockEntity(blockEntity, merged)
}

internal fun MinecraftServer.place(
    level: ServerLevel,
    position: BlockPos,
    block: ParsedBlock,
    flags: Int,
): Boolean {
    if (!level.isInWorldBounds(position)) {
        invalidInput("Position ${position.x} ${position.y} ${position.z} is outside the world")
    }

    EditJournal.before(level, position)
    val changed = level.setBlock(position, block.state, flags)
    val nbt = block.nbt ?: return changed

    val blockEntity =
        level.getBlockEntity(position)
            ?: invalidInput("${block.state.block} has no block entity data")

    level.loadBlockEntity(blockEntity, nbt)
    return true
}

private fun blockEntityAt(level: ServerLevel, position: BlockCoordinates): BlockEntity =
    level.getBlockEntity(position.toMc())
        ?: notFound("No block entity at ${position.x} ${position.y} ${position.z}")

internal fun updateFlags(noUpdates: Boolean): Int =
    if (noUpdates) {
        Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SKIP_ON_PLACE
    } else {
        Block.UPDATE_ALL
    }
