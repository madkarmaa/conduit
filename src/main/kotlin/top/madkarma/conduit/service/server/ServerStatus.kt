package top.madkarma.conduit.service.server

import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.Conduit

@Serializable
data class ServerInfo(
    val minecraftVersion: String,
    val modVersion: String,
    val motd: String,
    val dedicated: Boolean,
    val playerCount: Int,
    val maxPlayers: Int,
    val dimensions: List<String>,
    val port: Int,
    val defaultGameMode: String,
    val seed: Long,
    val difficulty: String,
    val tickCount: Int,
)

@Serializable data class ModInfo(val id: String, val version: String)

/** The mods in the mods folder, and how many libraries they bundle. */
@Serializable data class InstalledMods(val mods: List<ModInfo>, val bundled: Int)

@Serializable
data class Performance(
    val mspt: Double,
    val tps: Double,
    val targetTps: Double,
    val frozen: Boolean,
    val memoryUsedBytes: Long,
    val memoryMaxBytes: Long,
)

internal fun MinecraftServer.describeServer(): ServerInfo =
    ServerInfo(
        minecraftVersion = SharedConstants.getCurrentVersion().name(),
        modVersion = Conduit.version,
        motd = motd,
        dedicated = isDedicatedServer,
        playerCount = playerCount,
        maxPlayers = maxPlayers,
        dimensions = levelKeys().map { it.identifier().toString() },
        port = port,
        defaultGameMode = defaultGameType.serializedName,
        seed = overworld().seed,
        difficulty = worldData.difficulty.serializedName,
        tickCount = tickCount,
    )

// Mods bundle libraries and submodules inside their jar, which are rarely what someone looks for.
internal fun installedMods(): InstalledMods {
    val all = FabricLoader.getInstance().allMods
    val topLevel = all.filter { it.containingMod.isEmpty }
    val mods =
        topLevel
            .map { ModInfo(it.metadata.id, it.metadata.version.friendlyString) }
            .sortedBy { it.id }
    return InstalledMods(mods, all.size - topLevel.size)
}

fun MinecraftServer.performance(): Performance {
    val runtime = Runtime.getRuntime()
    val mspt = averageTickTimeNanos / 1_000_000.0
    val rate = tickRateManager()
    val target = rate.tickrate().toDouble()

    return Performance(
        mspt = mspt,
        tps = if (mspt <= 0.0) target else minOf(target, 1000.0 / mspt),
        targetTps = target,
        frozen = rate.isFrozen,
        memoryUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        memoryMaxBytes = runtime.maxMemory(),
    )
}
