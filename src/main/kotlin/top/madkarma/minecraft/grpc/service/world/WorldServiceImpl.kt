package top.madkarma.minecraft.grpc.service.world

import com.google.protobuf.Empty
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.game.level
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.proto.Biome
import top.madkarma.minecraft.grpc.proto.BlockInfo
import top.madkarma.minecraft.grpc.proto.BlockRegion
import top.madkarma.minecraft.grpc.proto.BreakBlockRequest
import top.madkarma.minecraft.grpc.proto.ChunkInfo
import top.madkarma.minecraft.grpc.proto.ChunkRequest
import top.madkarma.minecraft.grpc.proto.DimensionList
import top.madkarma.minecraft.grpc.proto.DimensionRef
import top.madkarma.minecraft.grpc.proto.ExplodeRequest
import top.madkarma.minecraft.grpc.proto.FillRequest
import top.madkarma.minecraft.grpc.proto.GameRules
import top.madkarma.minecraft.grpc.proto.GetBlockRequest
import top.madkarma.minecraft.grpc.proto.GetBlocksRequest
import top.madkarma.minecraft.grpc.proto.GetHeightRequest
import top.madkarma.minecraft.grpc.proto.GetHeightResponse
import top.madkarma.minecraft.grpc.proto.Nbt
import top.madkarma.minecraft.grpc.proto.SetBlockEntityRequest
import top.madkarma.minecraft.grpc.proto.SetBlockRequest
import top.madkarma.minecraft.grpc.proto.SetBlocksRequest
import top.madkarma.minecraft.grpc.proto.SetBlocksResponse
import top.madkarma.minecraft.grpc.proto.SetChunkForcedRequest
import top.madkarma.minecraft.grpc.proto.SetGameRuleRequest
import top.madkarma.minecraft.grpc.proto.SetTimeRequest
import top.madkarma.minecraft.grpc.proto.SetWeatherRequest
import top.madkarma.minecraft.grpc.proto.SpawnPoint
import top.madkarma.minecraft.grpc.proto.TimeInfo
import top.madkarma.minecraft.grpc.proto.WeatherInfo
import top.madkarma.minecraft.grpc.proto.WorldServiceGrpcKt
import top.madkarma.minecraft.grpc.proto.biome
import top.madkarma.minecraft.grpc.proto.getHeightResponse
import top.madkarma.minecraft.grpc.proto.nbt
import top.madkarma.minecraft.grpc.proto.setBlocksResponse

private val EMPTY: Empty = Empty.getDefaultInstance()

class WorldServiceImpl : WorldServiceGrpcKt.WorldServiceCoroutineImplBase() {
    override suspend fun listDimensions(request: Empty): DimensionList =
        GameServer.run { it.describeDimensions() }

    override suspend fun getBlock(request: GetBlockRequest): BlockInfo = GameServer.run {
        it.readBlock(request)
    }

    override suspend fun getBlocks(request: GetBlocksRequest): BlockRegion = GameServer.run {
        it.readRegion(request)
    }

    override suspend fun setBlock(request: SetBlockRequest): Empty = GameServer.run {
        it.writeBlock(request)
        EMPTY
    }

    override suspend fun setBlocks(request: SetBlocksRequest): SetBlocksResponse =
        GameServer.run { server -> setBlocksResponse { changed = server.writeBlocks(request) } }

    override suspend fun fill(request: FillRequest): SetBlocksResponse =
        GameServer.run { server -> setBlocksResponse { changed = server.fillRegion(request) } }

    override suspend fun breakBlock(request: BreakBlockRequest): Empty = GameServer.run {
        it.level(request.dimension).destroyBlock(request.pos.toMc(), request.dropItems)
        EMPTY
    }

    override suspend fun getBlockEntity(request: GetBlockRequest): Nbt =
        GameServer.run { server -> nbt { snbt = server.readBlockEntity(request) } }

    override suspend fun setBlockEntity(request: SetBlockEntityRequest): Empty = GameServer.run {
        it.mergeBlockEntity(request)
        EMPTY
    }

    override suspend fun getBiome(request: GetBlockRequest): Biome = GameServer.run { server ->
        biome {
            id =
                server.biomeAt(request)
        }
    }

    override suspend fun getHeight(request: GetHeightRequest): GetHeightResponse =
        GameServer.run { server -> getHeightResponse { y = server.heightAt(request) } }

    override suspend fun getChunk(request: ChunkRequest): ChunkInfo = GameServer.run {
        it.describeChunk(request)
    }

    override suspend fun setChunkForced(request: SetChunkForcedRequest): Empty = GameServer.run {
        it.level(request.dimension).setChunkForced(request.chunkX, request.chunkZ, request.forced)
        EMPTY
    }

    override suspend fun getTime(request: DimensionRef): TimeInfo = GameServer.run {
        it.timeOf(request)
    }

    override suspend fun setTime(request: SetTimeRequest): Empty = GameServer.run {
        it.changeTime(request)
        EMPTY
    }

    override suspend fun getWeather(request: DimensionRef): WeatherInfo = GameServer.run {
        it.weatherOf(request)
    }

    override suspend fun setWeather(request: SetWeatherRequest): Empty = GameServer.run {
        it.changeWeather(request)
        EMPTY
    }

    override suspend fun getGameRules(request: Empty): GameRules = GameServer.run {
        it.listGameRules()
    }

    override suspend fun setGameRule(request: SetGameRuleRequest): Empty = GameServer.run {
        it.changeGameRule(request)
        EMPTY
    }

    override suspend fun getSpawn(request: Empty): SpawnPoint = GameServer.run { it.spawnPoint() }

    override suspend fun setSpawn(request: SpawnPoint): Empty = GameServer.run {
        it.changeSpawnPoint(request)
        EMPTY
    }

    override suspend fun explode(request: ExplodeRequest): Empty = GameServer.run {
        it.explode(request)
        EMPTY
    }
}
