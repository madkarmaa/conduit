package top.madkarma.conduit.service.world

import kotlinx.serialization.Serializable
import net.minecraft.core.SectionPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import top.madkarma.conduit.game.enumByName
import top.madkarma.conduit.game.level

@Serializable
data class DimensionInfo(
    val id: String,
    val minY: Int,
    val maxY: Int,
    val loadedChunks: Int,
    val entityCount: Int,
    val playerCount: Int,
)

@Serializable
data class ChunkInfo(
    val loaded: Boolean,
    val forced: Boolean,
    val entityCount: Int = 0,
    val blockEntityCount: Int = 0,
)

internal fun MinecraftServer.describeDimensions(): List<DimensionInfo> = allLevels.map { level ->
    DimensionInfo(
        id = level.dimension().identifier().toString(),
        minY = level.minY,
        maxY = level.maxY,
        loadedChunks = level.chunkSource.loadedChunksCount,
        entityCount = level.allEntities.count(),
        playerCount = level.players().size,
    )
}

/** A block column. */
@Serializable data class Column(val x: Int, val z: Int)

/**
 * The y of the first free block above the surface of a column. Heightmap is WORLD_SURFACE,
 * MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES or OCEAN_FLOOR.
 */
internal fun MinecraftServer.heightAt(dimension: String?, x: Int, z: Int, heightmap: String?): Int {
    val type = enumByName(heightmap, Heightmap.Types.MOTION_BLOCKING, "heightmap")
    val level = level(dimension)

    // Heightmaps of unloaded chunks read as the world bottom, so this loads the chunk first.
    level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
    return level.getHeight(type, x, z)
}

internal fun MinecraftServer.describeChunk(
    dimension: String?,
    chunkX: Int,
    chunkZ: Int,
): ChunkInfo {
    val level = level(dimension)
    val forced = level.forceLoadedChunks.contains(ChunkPos.pack(chunkX, chunkZ))

    val chunk =
        level.chunkSource.getChunkNow(chunkX, chunkZ)
            ?: return ChunkInfo(loaded = false, forced = forced)

    val minX = chunkX * 16.0
    val minZ = chunkZ * 16.0
    val box = AABB(minX, level.minY.toDouble(), minZ, minX + 16, level.maxY + 1.0, minZ + 16)

    return ChunkInfo(
        loaded = true,
        forced = forced,
        entityCount = level.getEntities(null as Entity?, box) { true }.size,
        blockEntityCount = chunk.blockEntities.size,
    )
}

internal fun MinecraftServer.changeChunkForced(
    dimension: String?,
    chunkX: Int,
    chunkZ: Int,
    forced: Boolean,
) {
    level(dimension).setChunkForced(chunkX, chunkZ, forced)
}
