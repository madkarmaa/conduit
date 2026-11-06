package top.madkarma.minecraft.grpc.service.player

import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtUtils
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.util.Util
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.storage.LevelResource
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.notFound
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

// Saved player data is read and written on the server thread, each call within one
// GameServer.run block. Logins load this data on the server thread too, so a login never reads a
// half-written file and never loads data that an edit is about to replace. The files are small,
// which makes this the one place where file I/O on the server thread is acceptable.

private const val DATA_SUFFIX = ".dat"

private fun MinecraftServer.dataDirectory(): Path = getWorldPath(LevelResource.PLAYER_DATA_DIR)

private fun MinecraftServer.dataFile(id: UUID): Path = dataDirectory().resolve("$id$DATA_SUFFIX")

internal fun MinecraftServer.resolvePlayerId(ref: String): UUID {
    if (ref.isBlank()) invalidArgument("A player name or UUID is required")

    runCatching { UUID.fromString(ref) }.getOrNull()?.let { return it }
    playerList.getPlayerByName(ref)?.let { return it.uuid }

    // The name cache's own lookup by name asks the Mojang API on a miss, which must not block the
    // server thread, so the cached names of the known players are compared instead.
    val known = knownPlayerIds().firstOrNull { lastKnownName(it).equals(ref, ignoreCase = true) }
    if (known != null) return known

    if (!usesAuthentication()) return UUIDUtil.createOfflinePlayerUUID(ref)
    notFound("Unknown player: $ref")
}

internal fun MinecraftServer.isOnline(id: UUID): Boolean = playerList.getPlayer(id) != null

internal fun MinecraftServer.lastKnownName(id: UUID): String =
    playerList.getPlayer(id)?.gameProfile?.name
        ?: services().nameToIdCache().get(id).map { it.name() }.orElse("")

internal fun MinecraftServer.lastPlayed(id: UUID): Long {
    if (isOnline(id)) return System.currentTimeMillis()
    return Files.getLastModifiedTime(dataFile(id)).toMillis()
}

internal fun MinecraftServer.knownPlayerIds(): List<UUID> {
    val directory = dataDirectory()
    if (!Files.isDirectory(directory)) return emptyList()

    val files = Files.list(directory).use { it.toList() }
    return files.mapNotNull { file ->
        val name = file.fileName.toString()
        if (!name.endsWith(DATA_SUFFIX)) return@mapNotNull null
        runCatching { UUID.fromString(name.removeSuffix(DATA_SUFFIX)) }.getOrNull()
    }
}

internal fun MinecraftServer.requireOffline(id: UUID) {
    if (isOnline(id)) failedPrecondition("Player is online, use the online RPCs")
    if (isJoining(id)) failedPrecondition("Player is joining, try again once they are online")
}

// A joining player's data is loaded before they are in the player list, and their first save
// would overwrite any edit made in between.
private fun MinecraftServer.isJoining(id: UUID): Boolean = connection.connections.any {
    val listener = it.packetListener as? ServerConfigurationPacketListenerImpl
    listener?.owner?.id == id
}

/** Reads saved data like the game does at login, but fails instead of using the backup. */
internal fun MinecraftServer.readPlayerData(id: UUID): CompoundTag {
    val file = dataFile(id)
    if (!Files.isRegularFile(file)) notFound("No saved data for player $id")

    try {
        val saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
        return DataFixTypes.PLAYER.updateToCurrentVersion(
            fixerUpper,
            saved,
            NbtUtils.getDataVersion(saved),
        )
    } catch (exception: Exception) {
        failedPrecondition("Saved data of player $id is corrupt: $exception")
    }
}

/** Writes like PlayerDataStorage.save, which only saves online players. */
internal fun MinecraftServer.writePlayerData(id: UUID, data: CompoundTag) {
    NbtUtils.addCurrentDataVersion(data)

    val directory = dataDirectory()
    val temporary = Files.createTempFile(directory, "$id-", DATA_SUFFIX)

    NbtIo.writeCompressed(data, temporary)
    Util.safeReplaceFile(dataFile(id), temporary, directory.resolve("$id.dat_old"))
}
