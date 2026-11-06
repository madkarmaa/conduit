package top.madkarma.minecraft.grpc.service.structure

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
import top.madkarma.minecraft.grpc.game.level
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.mixin.StructureTemplateAccessor
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument

/** Checks the placement options before any structure data is received or loaded. */
internal fun checkPlacement(placement: StructurePlacement) {
    if (placement.hasIntegrity() && placement.integrity !in 0f..1f) {
        invalidArgument("Integrity must be between 0 and 1")
    }
}

internal fun MinecraftServer.placeStructure(
    template: StructureTemplate,
    placement: StructurePlacement,
): ImportStructureResponse {
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

    return importStructureResponse {
        size = template.getSize(settings.rotation).toProto()
        this.blockCount = blockCount
        entityCount = if (settings.isIgnoreEntities) 0 else contents.entityInfoList.size
    }
}

private fun placeSettings(placement: StructurePlacement): StructurePlaceSettings {
    val settings =
        StructurePlaceSettings()
            .setRotation(placement.rotation.toMc())
            .setMirror(placement.mirror.toMc())
            .setIgnoreEntities(!placement.includeEntities)
            .setKnownShape(placement.noUpdates)

    if (placement.hasIntegrity() && placement.integrity < 1f) {
        settings.addProcessor(BlockRotProcessor(placement.integrity))
    }

    return settings
}

/**
 * Forcing a chunk keeps it loaded while the structure is placed, and getChunk loads it right away
 * instead of on a later tick. Only the forces added here are released, so chunks that were already
 * forced, e.g. with SetChunkForced, stay forced.
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

private fun StructureRotation.toMc(): Rotation =
    when (this) {
        StructureRotation.STRUCTURE_ROTATION_UNSPECIFIED,
        StructureRotation.STRUCTURE_ROTATION_NONE -> Rotation.NONE
        StructureRotation.STRUCTURE_ROTATION_CLOCKWISE_90 -> Rotation.CLOCKWISE_90
        StructureRotation.STRUCTURE_ROTATION_CLOCKWISE_180 -> Rotation.CLOCKWISE_180
        StructureRotation.STRUCTURE_ROTATION_COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90
        StructureRotation.UNRECOGNIZED -> invalidArgument("Unknown rotation")
    }

private fun StructureMirror.toMc(): Mirror =
    when (this) {
        StructureMirror.STRUCTURE_MIRROR_UNSPECIFIED,
        StructureMirror.STRUCTURE_MIRROR_NONE -> Mirror.NONE
        StructureMirror.STRUCTURE_MIRROR_LEFT_RIGHT -> Mirror.LEFT_RIGHT
        StructureMirror.STRUCTURE_MIRROR_FRONT_BACK -> Mirror.FRONT_BACK
        StructureMirror.UNRECOGNIZED -> invalidArgument("Unknown mirror")
    }
