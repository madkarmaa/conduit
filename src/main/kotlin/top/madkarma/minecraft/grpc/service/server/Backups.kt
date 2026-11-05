package top.madkarma.minecraft.grpc.service.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import top.madkarma.minecraft.grpc.MinecraftGRPC
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.BackupList
import top.madkarma.minecraft.grpc.proto.BackupRequest
import top.madkarma.minecraft.grpc.proto.BackupResponse
import top.madkarma.minecraft.grpc.proto.backupResponse
import top.madkarma.minecraft.grpc.rpc.aborted
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_NAME_LENGTH = 64

private val UNSAFE_NAME_CHARACTERS = Regex("[^A-Za-z0-9_-]")

private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private val backupInProgress = AtomicBoolean(false)

private class BackupPaths(val world: Path, val target: Path)

internal suspend fun createBackup(request: BackupRequest): BackupResponse {
    if (request.name.length > MAX_NAME_LENGTH) {
        invalidArgument("Backup name must be at most $MAX_NAME_LENGTH characters")
    }

    if (!backupInProgress.compareAndSet(false, true)) aborted("A backup is already in progress")

    try {
        return backUpWorld(request)
    } finally {
        backupInProgress.set(false)
    }
}

internal suspend fun describeBackups(): BackupList {
    val directory = GameServer.run { it.backupDirectory() }
    return withContext(Dispatchers.IO) { listBackupFiles(directory) }
}

private suspend fun backUpWorld(request: BackupRequest): BackupResponse {
    val startNanos = System.nanoTime()
    val previousNoSave = GameServer.run { it.pauseAutosave() }

    try {
        val paths = GameServer.run { it.saveForBackup(request) }
        val size = withContext(Dispatchers.IO) { zipWorld(paths.world, paths.target) }

        return backupResponse {
            path = paths.target.toString()
            sizeBytes = size
            durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
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

private fun MinecraftServer.saveForBackup(request: BackupRequest): BackupPaths {
    // Forced, because autosave is now off for every level and would skip the chunks otherwise.
    saveEverything(true, true, true)

    val world = getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
    val fileName = backupFileName(world.fileName.toString(), request)

    return BackupPaths(world, backupDirectory().resolve(fileName))
}

private fun MinecraftServer.backupDirectory(): Path =
    serverDirectory.resolve(MinecraftGRPC.config.backupDirectory).toAbsolutePath().normalize()

private fun backupFileName(worldName: String, request: BackupRequest): String {
    val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
    if (request.name.isBlank()) return "$worldName-$timestamp.zip"

    return "$worldName-$timestamp-${request.name.replace(UNSAFE_NAME_CHARACTERS, "_")}.zip"
}
