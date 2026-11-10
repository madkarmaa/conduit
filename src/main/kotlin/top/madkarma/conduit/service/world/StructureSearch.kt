package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.notFound

// The same search range as /locate.
private const val STRUCTURE_RADIUS_CHUNKS = 100

/** The nearest structure, with the box around its pieces when the game knows it. */
internal fun MinecraftServer.locateStructure(
    dimension: String?,
    structure: String,
    center: BlockPos,
): Located {
    val level = level(dimension)
    val structures = holders(Registries.STRUCTURE, structure, "structure")
    val found =
        level.chunkSource.generator.findNearestMapStructure(
            level,
            structures,
            center,
            STRUCTURE_RADIUS_CHUNKS,
            false,
        ) ?: notFound("No $structure within $STRUCTURE_RADIUS_CHUNKS chunks")

    val box = structureBox(level, found.first, found.second.value())
    val pos = box?.center ?: found.first
    return located(found.second, pos, center, horizontal = true).copy(box = box?.let(::boundsOf))
}

// The start chunk already knows the structure's pieces, so this generates no blocks.
private fun structureBox(level: ServerLevel, pos: BlockPos, structure: Structure): BoundingBox? {
    val chunk =
        level.getChunk(
            SectionPos.blockToSectionCoord(pos.x),
            SectionPos.blockToSectionCoord(pos.z),
            ChunkStatus.STRUCTURE_STARTS,
        )
    val start = chunk.getStartForStructure(structure) ?: return null
    return if (start.isValid) start.boundingBox else null
}

internal fun boundsOf(box: BoundingBox) =
    Bounds(
        BlockCoordinates(box.minX(), box.minY(), box.minZ()),
        BlockCoordinates(box.maxX(), box.maxY(), box.maxZ()),
    )
