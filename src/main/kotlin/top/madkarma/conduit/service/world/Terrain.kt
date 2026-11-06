package top.madkarma.conduit.service.world

import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.SectionPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import top.madkarma.conduit.game.enumByName
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.proto.*

internal fun MinecraftServer.describeDimensions(): DimensionList = dimensionList {
    for (level in allLevels) {
        dimensions += dimensionInfo {
            id = level.dimension().identifier().toString()
            minY = level.minY
            maxY = level.maxY
            loadedChunks = level.chunkSource.loadedChunksCount
            entityCount = level.allEntities.count()
            playerCount = level.players().size
        }
    }
}

internal fun MinecraftServer.biomeAt(request: GetBlockRequest): Biome = biome {
    id =
        level(request.dimension)
            .getBiome(request.pos.toMc())
            .unwrapKey()
            .getOrNull()
            ?.identifier()
            ?.toString() ?: ""
}

internal fun MinecraftServer.heightAt(request: GetHeightRequest): GetHeightResponse {
    val type = enumByName(request.heightmap, Heightmap.Types.MOTION_BLOCKING, "heightmap")
    val level = level(request.dimension)

    // Heightmaps of unloaded chunks read as the world bottom, so this loads the chunk first.
    val chunkX = SectionPos.blockToSectionCoord(request.x)
    val chunkZ = SectionPos.blockToSectionCoord(request.z)

    level.getChunk(chunkX, chunkZ)
    return getHeightResponse { y = level.getHeight(type, request.x, request.z) }
}

internal fun MinecraftServer.describeChunk(request: ChunkRequest): ChunkInfo {
    val level = level(request.dimension)
    val forced = level.forceLoadedChunks.contains(ChunkPos.pack(request.chunkX, request.chunkZ))

    val chunk =
        level.chunkSource.getChunkNow(request.chunkX, request.chunkZ)
            ?: return chunkInfo { this.forced = forced }

    val minX = request.chunkX * 16.0
    val minZ = request.chunkZ * 16.0
    val box = AABB(minX, level.minY.toDouble(), minZ, minX + 16, level.maxY + 1.0, minZ + 16)

    return chunkInfo {
        loaded = true
        this.forced = forced
        entityCount = level.getEntities(null as Entity?, box) { true }.size
        blockEntityCount = chunk.blockEntities.size
    }
}

internal fun MinecraftServer.changeChunkForced(request: SetChunkForcedRequest) {
    level(request.dimension).setChunkForced(request.chunkX, request.chunkZ, request.forced)
}
