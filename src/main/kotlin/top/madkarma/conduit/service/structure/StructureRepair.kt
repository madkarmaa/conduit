package top.madkarma.conduit.service.structure

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.notFound

/** One template block of a structure, where it belongs in the world, and what is there now. */
internal class TemplateBlock(val pos: BlockPos, val expected: BlockState, val current: BlockState) {
    /** Blocks that players or explosions removed, which leave air, fluids or plants behind. */
    val missing: Boolean
        get() = current.block != expected.block && current.canBeReplaced()

    /** Another block took the place, such as one a player put there or a processor's variant. */
    val different: Boolean
        get() = current.block != expected.block && !current.canBeReplaced()
}

/** The template blocks of a generated structure compared with the world. */
internal class StructureBlocks(
    val structure: String,
    val blocks: List<TemplateBlock>,
    val pieces: Int,
    /**
     * Pieces that the game builds without a template, such as mineshaft corridors, or fits to the
     * terrain, such as village streets.
     */
    val skippedPieces: Int,
)

@Serializable
data class BlockDifference(
    val block: String,
    val count: Int,
    /** A few of the positions, and what is there now for different blocks. */
    val positions: List<BlockCoordinates>,
    val now: String? = null,
)

/**
 * The template blocks of the structure at a position, or of the one named, placed the way the game
 * placed each piece. Air in a template is left out, since a repair never removes blocks.
 */
internal fun MinecraftServer.structureBlocks(
    dimension: String?,
    pos: BlockPos,
    structure: String?,
): StructureBlocks {
    val level = level(dimension)
    val starts = startsAt(level, pos).filter { structure == null || it.first == structure }
    // Several structures can overlap, and the one with a piece at the position is the one meant.
    val (id, start) =
        starts.firstOrNull { pieceAt(it.second, pos) != null }
            ?: starts.firstOrNull()
            ?: notFound("No generated ${structure ?: "structure"} at ${pos.toShortString()}")

    var skipped = 0
    val blocks = mutableListOf<TemplateBlock>()
    for (piece in start.pieces) {
        val placed = piece.templatePiece()?.let { placedPiece(level, it) }
        if (placed == null) {
            skipped++
            continue
        }
        blocks +=
            placed.blocks.map { block ->
                val worldPos = block.pos.above(placed.shift)
                TemplateBlock(worldPos, block.state, level.getBlockState(worldPos))
            }
    }

    return StructureBlocks(id, blocks, start.pieces.size, skipped)
}

/** Groups template blocks by their block, the largest group first, with a few positions each. */
internal fun differences(blocks: List<TemplateBlock>, samples: Int): List<BlockDifference> =
    blocks
        .groupBy { it.expected.block }
        .entries
        .sortedByDescending { it.value.size }
        .map { (block, group) ->
            BlockDifference(
                blockId(block),
                group.size,
                group.take(samples).map { BlockCoordinates(it.pos.x, it.pos.y, it.pos.z) },
                group.first().current.block.takeIf { group.first().different }?.let(::blockId),
            )
        }

private fun blockId(block: Block) = BuiltInRegistries.BLOCK.getKey(block).toString()
