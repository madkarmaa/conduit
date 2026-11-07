package top.madkarma.conduit.service.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import net.minecraft.world.level.storage.LevelResource

@Serializable
data class BackupFile(
    val name: String,
    val sizeBytes: Long,
    /** Unix time in milliseconds. */
    val createdTimestampMs: Long,
)

/** Zips the world folder into target and returns the size of the zip file. */
internal fun zipWorld(world: Path, target: Path): Long {
    Files.createDirectories(target.parent)

    // A hidden temporary file keeps a half-written zip from being listed as a backup.
    val temporary = target.resolveSibling(".${target.fileName}.tmp")

    try {
        writeZip(world, temporary)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }

    return Files.size(target)
}

internal fun listBackupFiles(directory: Path): List<BackupFile> {
    if (!Files.isDirectory(directory)) return emptyList()

    val files =
        Files.list(directory).use { paths ->
            paths
                .filter { it.fileName.toString().endsWith(".zip") && Files.isRegularFile(it) }
                .sorted()
                .toList()
        }

    return files.map { file ->
        val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
        BackupFile(
            name = file.fileName.toString(),
            sizeBytes = attributes.size(),
            createdTimestampMs = attributes.creationTime().toMillis(),
        )
    }
}

// Entries start with the world folder name, like the game's own world backups.
private fun writeZip(world: Path, target: Path) {
    val lockFile = world.resolve(LevelResource.LOCK_FILE.id())

    ZipOutputStream(Files.newOutputStream(target).buffered()).use { zip ->
        Files.walk(world).use { files ->
            files
                .filter { Files.isRegularFile(it) && it != lockFile }
                .forEach { file ->
                    val entryPath = world.fileName.resolve(world.relativize(file))
                    zip.putNextEntry(ZipEntry(entryPath.joinToString("/")))
                    Files.copy(file, zip)
                    zip.closeEntry()
                }
        }
    }
}
