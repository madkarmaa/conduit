package top.madkarma.conduit.service.player

import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.jvm.optionals.getOrNull
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
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.notFound

// This reads and writes saved player data on the server thread, each call within one
// GameServer.run block. Logins load this data on the server thread too, so a login never reads a
// half-written file and never loads data that an edit is about to replace. The files are small,
// which makes this the one place where file I/O on the server thread is acceptable.

private const val DATA_SUFFIX = ".dat"

private fun MinecraftServer.dataDirectory(): Path = getWorldPath(LevelResource.PLAYER_DATA_DIR)

private fun MinecraftServer.dataFile(id: UUID): Path = dataDirectory().resolve("$id$DATA_SUFFIX")

internal fun MinecraftServer.resolvePlayerId(ref: String): UUID {
    if (ref.isBlank()) invalidInput("A player name or UUID is required")

    runCatching { UUID.fromString(ref) }
        .getOrNull()
        ?.let {
            return it
        }

    playerList.getPlayerByName(ref)?.let {
        return it.uuid
    }

    // The name cache's own lookup by name calls the Mojang API on a miss, which would block the
    // server thread. Comparing the cached names of the known players avoids that.
    val known = knownPlayerIds().firstOrNull { lastKnownName(it).equals(ref, ignoreCase = true) }
    if (known != null) return known

    if (!usesAuthentication()) return UUIDUtil.createOfflinePlayerUUID(ref)
    notFound("Unknown player: $ref")
}

internal fun MinecraftServer.isOnline(id: UUID): Boolean = playerList.getPlayer(id) != null

internal fun MinecraftServer.lastKnownName(id: UUID): String =
    playerList.getPlayer(id)?.gameProfile?.name
        ?: services().nameToIdCache().get(id).getOrNull()?.name().orEmpty()

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
    if (isOnline(id)) failedPrecondition("Player is online; use the tools for online players")
    if (isJoining(id)) failedPrecondition("Player is joining, try again once they are online")
}

// The game loads a joining player's data before the player is in the player list, and their
// first save would overwrite any edit made in between.
private fun MinecraftServer.isJoining(id: UUID): Boolean =
    connection.connections.any {
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
