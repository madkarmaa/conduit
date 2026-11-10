package top.madkarma.conduit.service.structure

import kotlin.math.abs
import kotlin.math.max
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import top.madkarma.conduit.mixin.StructureTemplateAccessor
import top.madkarma.conduit.mixin.TemplateStructurePieceAccessor

// Enough blocks to tell the height of a piece, and the farthest the game may move one.
private const val SHIFT_SAMPLES = 64
private const val MAX_SHIFT = 128

// Blocks that only steer placement and never end up in the world as they are.
private val PLACEMENT_BLOCKS = setOf(Blocks.STRUCTURE_VOID, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW)

/** A structure piece placed from a template, with the settings the game placed it with. */
internal class TemplatePiece(
    val template: Identifier,
    val position: BlockPos,
    val settings: StructurePlaceSettings,
    /** Whether the saved position has the height the game built the piece at. */
    val heightKnown: Boolean,
)

/**
 * The template, origin and settings of a piece placed from a template. Jigsaw pieces place their
 * template rotated around their corner, without a mirror, at the height they were planned at. Null
 * for pieces without a template, and for terrain fitted ones, whose blocks have no fixed place.
 */
internal fun StructurePiece.templatePiece(): TemplatePiece? =
    when (this) {
        is PoolElementStructurePiece -> {
            // Pieces fitted to the terrain, such as village streets, move each block to the ground.
            val element =
                (element as? SinglePoolElement)?.takeIf { it.projection == Projection.RIGID }
            element?.let {
                TemplatePiece(
                    it.templateLocation,
                    position,
                    StructurePlaceSettings().setRotation(rotation),
                    heightKnown = true,
                )
            }
        }

        is TemplateStructurePiece -> {
            val accessor = this as TemplateStructurePieceAccessor
            TemplatePiece(
                Identifier.parse(accessor.templateName),
                accessor.templatePosition,
                accessor.placeSettings,
                heightKnown = false,
            )
        }

        else -> null
    }

/** A template block where the saved piece puts it, before any height shift. */
internal class PlacedBlock(val pos: BlockPos, val state: BlockState)

/** The blocks of a template piece, and how far the game moved the piece up or down to build it. */
internal class PlacedPiece(val blocks: List<PlacedBlock>, val shift: Int)

/**
 * The blocks of a template piece, placed the way the game placed it. Air in a template is left out,
 * since a repair never removes blocks. Null when the template is gone.
 */
internal fun MinecraftServer.placedPiece(level: ServerLevel, piece: TemplatePiece): PlacedPiece? {
    val template = structureTemplateManager.get(piece.template).orElse(null) ?: return null
    val palettes = (template as StructureTemplateAccessor).palettes
    if (palettes.isEmpty()) return PlacedPiece(emptyList(), 0)

    val settings = piece.settings
    val blocks =
        settings
            .getRandomPalette(palettes, piece.position)
            .blocks()
            .filter { !it.state.isAir && it.state.block !in PLACEMENT_BLOCKS }
            .map { info ->
                val pos = StructureTemplate.calculateRelativePosition(settings, info.pos)
                PlacedBlock(
                    pos.offset(piece.position),
                    info.state.mirror(settings.mirror).rotate(settings.rotation),
                )
            }

    return PlacedPiece(blocks, if (piece.heightKnown) 0 else heightShift(level, blocks))
}

/**
 * The height shift at which most of a sample of the blocks match the world, nearest to none on a
 * tie. Pieces such as igloos only find the ground when the game builds them.
 */
private fun heightShift(level: ServerLevel, blocks: List<PlacedBlock>): Int {
    if (blocks.isEmpty()) return 0

    val step = max(1, blocks.size / SHIFT_SAMPLES)
    val sample = blocks.filterIndexed { index, _ -> index % step == 0 }
    val matches = { shift: Int ->
        sample.count { level.getBlockState(it.pos.above(shift)).block == it.state.block }
    }

    val best =
        (-MAX_SHIFT..MAX_SHIFT).maxWithOrNull(
            compareBy<Int> { matches(it) }.thenByDescending { abs(it) },
        ) ?: 0
    return if (matches(best) == 0) 0 else best
}
