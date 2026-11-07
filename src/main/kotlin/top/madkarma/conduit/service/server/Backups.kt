package top.madkarma.conduit.service.server

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.aborted
import top.madkarma.conduit.game.invalidInput

private const val MAX_NAME_LENGTH = 64

private val UNSAFE_NAME_CHARACTERS = Regex("[^A-Za-z0-9_-]")

private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private val backupInProgress = AtomicBoolean(false)

private class BackupPaths(val world: Path, val target: Path)

@Serializable
data class BackupResult(
    /** Absolute path of the zip file. */
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
)

/**
 * Saves the world and zips it into the backup directory while the server keeps running. The name is
 * appended to the file name. Autosave pauses during the backup.
 */
internal suspend fun createBackup(name: String?): BackupResult {
    if (name.orEmpty().length > MAX_NAME_LENGTH) {
        invalidInput("Backup name must be at most $MAX_NAME_LENGTH characters")
    }

    if (!backupInProgress.compareAndSet(false, true)) aborted("A backup is already in progress")

    try {
        return backUpWorld(name.orEmpty())
    } finally {
        backupInProgress.set(false)
    }
}

internal suspend fun describeBackups(): List<BackupFile> {
    val directory = GameServer.run { it.backupDirectory() }
    return withContext(Dispatchers.IO) { listBackupFiles(directory) }
}

private suspend fun backUpWorld(name: String): BackupResult {
    val startNanos = System.nanoTime()
    val previousNoSave = GameServer.run { it.pauseAutosave() }

    try {
        val paths = GameServer.run { it.saveForBackup(name) }
        val size = withContext(Dispatchers.IO) { zipWorld(paths.world, paths.target) }

        return BackupResult(
            path = paths.target.toString(),
            sizeBytes = size,
            durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
        )
    } finally {
        // Autosave must come back even when the client cancels the call.
        withContext(NonCancellable) { GameServer.run { restoreAutosave(previousNoSave) } }
    }
}

/** Turns off autosave for every level and returns the previous noSave flag of each level. */
private fun MinecraftServer.pauseAutosave(): Map<ServerLevel, Boolean> {
    val previousNoSave = allLevels.associateWith { it.noSave }
    allLevels.forEach { it.noSave = true }
    return previousNoSave
}

private fun restoreAutosave(previousNoSave: Map<ServerLevel, Boolean>) {
    for ((level, noSave) in previousNoSave) level.noSave = noSave
}

private fun MinecraftServer.saveForBackup(name: String): BackupPaths {
    // Forced, because autosave is now off for every level and would skip the chunks otherwise.
    saveEverything(true, true, true)

    val world = getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
    val fileName = backupFileName(world.fileName.toString(), name)

    return BackupPaths(world, backupDirectory().resolve(fileName))
}

private fun MinecraftServer.backupDirectory(): Path =
    serverDirectory.resolve(Conduit.gameConfig.backupDirectory).toAbsolutePath().normalize()

private fun backupFileName(worldName: String, name: String): String {
    val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
    if (name.isBlank()) return "$worldName-$timestamp.zip"

    return "$worldName-$timestamp-${name.replace(UNSAFE_NAME_CHARACTERS, "_")}.zip"
}
