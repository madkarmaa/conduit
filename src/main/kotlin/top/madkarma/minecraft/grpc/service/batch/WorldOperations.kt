package top.madkarma.minecraft.grpc.service.batch

import net.minecraft.server.MinecraftServer
import top.madkarma.minecraft.grpc.proto.Operation
import top.madkarma.minecraft.grpc.proto.Operation.OperationCase.*
import top.madkarma.minecraft.grpc.proto.OperationResult
import top.madkarma.minecraft.grpc.service.world.*

/** Returns false when the operation is not a WorldService operation. */
internal fun MinecraftServer.applyWorldOperation(
    operation: Operation,
    result: OperationResult.Builder,
): Boolean {
    when (operation.operationCase) {
        LIST_DIMENSIONS -> result.dimensions = describeDimensions()
        GET_BLOCK -> result.block = readBlock(operation.getBlock)
        GET_BLOCKS -> result.blocks = readRegion(operation.getBlocks)
        SET_BLOCK -> writeBlock(operation.setBlock)
        SET_BLOCKS -> result.setBlocks = writeBlocks(operation.setBlocks)
        FILL -> result.setBlocks = fillRegion(operation.fill)
        BREAK_BLOCK -> breakBlock(operation.breakBlock)
        GET_BLOCK_ENTITY -> result.nbt = readBlockEntity(operation.getBlockEntity)
        SET_BLOCK_ENTITY -> mergeBlockEntity(operation.setBlockEntity)
        GET_BIOME -> result.biome = biomeAt(operation.getBiome)
        GET_HEIGHT -> result.height = heightAt(operation.getHeight)
        GET_CHUNK -> result.chunk = describeChunk(operation.getChunk)
        SET_CHUNK_FORCED -> changeChunkForced(operation.setChunkForced)
        GET_TIME -> result.time = timeOf(operation.getTime)
        SET_TIME -> changeTime(operation.setTime)
        GET_WEATHER -> result.weather = weatherOf(operation.getWeather)
        SET_WEATHER -> changeWeather(operation.setWeather)
        GET_GAME_RULES -> result.gameRules = listGameRules()
        SET_GAME_RULE -> changeGameRule(operation.setGameRule)
        GET_SPAWN -> result.spawn = spawnPoint()
        SET_SPAWN -> changeSpawnPoint(operation.setSpawn)
        EXPLODE -> explode(operation.explode)
        GET_WORLD_BORDER -> result.worldBorder = worldBorderOf(operation.getWorldBorder)
        SET_WORLD_BORDER -> changeWorldBorder(operation.setWorldBorder)
        else -> return false
    }

    return true
}
