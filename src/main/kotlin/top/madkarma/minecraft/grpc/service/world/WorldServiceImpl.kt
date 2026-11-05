package top.madkarma.minecraft.grpc.service.world

import com.google.protobuf.Empty
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.*

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
        GameServer.run { it.writeBlocks(request) }

    override suspend fun fill(request: FillRequest): SetBlocksResponse =
        GameServer.run { it.fillRegion(request) }

    override suspend fun breakBlock(request: BreakBlockRequest): Empty = GameServer.run {
        it.breakBlock(request)
        EMPTY
    }

    override suspend fun getBlockEntity(request: GetBlockRequest): Nbt =
        GameServer.run { it.readBlockEntity(request) }

    override suspend fun setBlockEntity(request: SetBlockEntityRequest): Empty = GameServer.run {
        it.mergeBlockEntity(request)
        EMPTY
    }

    override suspend fun getBiome(request: GetBlockRequest): Biome = GameServer.run {
        it.biomeAt(request)
    }

    override suspend fun getHeight(request: GetHeightRequest): GetHeightResponse =
        GameServer.run { it.heightAt(request) }

    override suspend fun getChunk(request: ChunkRequest): ChunkInfo = GameServer.run {
        it.describeChunk(request)
    }

    override suspend fun setChunkForced(request: SetChunkForcedRequest): Empty = GameServer.run {
        it.changeChunkForced(request)
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

    override suspend fun getWorldBorder(request: DimensionRef): WorldBorderInfo =
        GameServer.run { it.worldBorderOf(request) }

    override suspend fun setWorldBorder(request: SetWorldBorderRequest): Empty = GameServer.run {
        it.changeWorldBorder(request)
        EMPTY
    }
}
