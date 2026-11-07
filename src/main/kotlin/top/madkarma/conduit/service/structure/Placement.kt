package top.madkarma.conduit.service.structure

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.toCoordinates
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mixin.StructureTemplateAccessor

enum class StructureRotation(val rotation: Rotation) {
    NONE(Rotation.NONE),
    CLOCKWISE_90(Rotation.CLOCKWISE_90),
    CLOCKWISE_180(Rotation.CLOCKWISE_180),
    COUNTERCLOCKWISE_90(Rotation.COUNTERCLOCKWISE_90),
}

enum class StructureMirror(val mirror: Mirror) {
    NONE(Mirror.NONE),
    LEFT_RIGHT(Mirror.LEFT_RIGHT),
    FRONT_BACK(Mirror.FRONT_BACK),
}

data class StructurePlacement(
    val dimension: String?,
    /**
     * Where the structure's lowest corner goes. Rotation and mirroring pivot around it, like /place
     * template.
     */
    val origin: BlockCoordinates,
    val rotation: StructureRotation = StructureRotation.NONE,
    val mirror: StructureMirror = StructureMirror.NONE,
    val includeEntities: Boolean = true,
    /** Chance between 0 and 1 that each block is placed. */
    val integrity: Float = 1f,
    /** Skips neighbor updates and physics, like /place template with strict. */
    val noUpdates: Boolean = false,
)

@Serializable
data class PlacementResult(
    /** Size of the placed structure, after rotation. */
    val size: BlockCoordinates,
    /** Blocks in the structure, including air. With an integrity below 1, fewer are placed. */
    val blockCount: Int,
    /** Entities in the structure, or 0 without entities. */
    val entityCount: Int,
)

/** Checks the placement options before any structure data is received or loaded. */
internal fun checkPlacement(placement: StructurePlacement) {
    if (placement.integrity !in 0f..1f) invalidInput("Integrity must be between 0 and 1")
}

internal fun MinecraftServer.placeStructure(
    template: StructureTemplate,
    placement: StructurePlacement,
): PlacementResult {
    checkVolume(template.size.volume)

    val level = level(placement.dimension)
    val origin = placement.origin.toMc()
    val settings = placeSettings(placement)
    val box = template.getBoundingBox(settings, origin)

    val insideWorld =
        Level.isInSpawnableBounds(box.minCorner()) && Level.isInSpawnableBounds(box.maxCorner())
    if (!insideWorld) failedPrecondition("The structure would extend outside the world")

    val contents = template as StructureTemplateAccessor
    // Without a random source in the settings, the palette choice depends only on the origin.
    val blockCount = settings.getRandomPalette(contents.palettes, origin).blocks().size
    val sideEffects = if (placement.noUpdates) Block.UPDATE_SKIP_ALL_SIDEEFFECTS else 0

    level.withChunksForced(box) {
        val flags = Block.UPDATE_CLIENTS or sideEffects
        template.placeInWorld(level, origin, origin, settings, level.random, flags)
    }

    return PlacementResult(
        size = template.getSize(settings.rotation).toCoordinates(),
        blockCount = blockCount,
        entityCount = if (settings.isIgnoreEntities) 0 else contents.entityInfoList.size,
    )
}

private fun placeSettings(placement: StructurePlacement): StructurePlaceSettings {
    val settings =
        StructurePlaceSettings()
            .setRotation(placement.rotation.rotation)
            .setMirror(placement.mirror.mirror)
            .setIgnoreEntities(!placement.includeEntities)
            .setKnownShape(placement.noUpdates)

    if (placement.integrity < 1f) {
        settings.addProcessor(BlockRotProcessor(placement.integrity))
    }

    return settings
}

/**
 * Forcing a chunk keeps it loaded during the placement, and getChunk loads it right away instead of
 * on a later tick. This only releases the forces it added, so chunks that were already forced, for
 * example with SetChunkForced, stay forced.
 */
private fun ServerLevel.withChunksForced(box: BoundingBox, placement: () -> Unit) {
    val chunks =
        ChunkPos.rangeClosed(
                ChunkPos.containing(box.minCorner()),
                ChunkPos.containing(box.maxCorner()),
            )
            .toList()
    val addedForces = chunks.filter { chunkSource.updateChunkForced(it, true) }

    try {
        for (chunk in chunks) getChunk(chunk.x(), chunk.z())
        placement()
    } finally {
        for (chunk in addedForces) chunkSource.updateChunkForced(chunk, false)
    }
}

private fun BoundingBox.minCorner(): BlockPos = BlockPos(minX(), minY(), minZ())

private fun BoundingBox.maxCorner(): BlockPos = BlockPos(maxX(), maxY(), maxZ())
