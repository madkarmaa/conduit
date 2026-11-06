package top.madkarma.conduit.service.server

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.proto.Performance
import top.madkarma.conduit.proto.ServerInfo
import top.madkarma.conduit.proto.performance
import top.madkarma.conduit.proto.serverInfo

internal fun MinecraftServer.describeServer(): ServerInfo {
    val server = this
    return serverInfo {
        minecraftVersion = SharedConstants.getCurrentVersion().name()
        modVersion = Conduit.version
        motd = server.motd
        dedicated = server.isDedicatedServer
        playerCount = server.playerCount
        maxPlayers = server.maxPlayers
        dimensions += server.levelKeys().map { it.identifier().toString() }
        port = server.port
        defaultGameMode = server.defaultGameType.serializedName
        seed = server.overworld().seed
        difficulty = server.worldData.difficulty.serializedName
        tickCount = server.tickCount
        mods += FabricLoader.getInstance().allMods.map { it.metadata.id }.sorted()
    }
}

fun MinecraftServer.performance(): Performance {
    val runtime = Runtime.getRuntime()
    val mspt = averageTickTimeNanos / 1_000_000.0
    val rate = tickRateManager()
    val target = rate.tickrate().toDouble()

    return performance {
        this.mspt = mspt

        tps = if (mspt <= 0.0) target else minOf(target, 1000.0 / mspt)
        targetTps = target

        frozen = rate.isFrozen

        memoryUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        memoryMaxBytes = runtime.maxMemory()
    }
}
