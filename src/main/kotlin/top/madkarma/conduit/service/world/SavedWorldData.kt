package top.madkarma.conduit.service.world

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.notFound
import top.madkarma.conduit.mixin.SavedDataStorageAccessor
import top.madkarma.conduit.service.player.tagsAt

private const val DATA_SUFFIX = ".dat"

// Saved data files hold the data under this key, next to the data version.
private const val DATA_KEY = "data"

/**
 * The ids of the saved data of the server, or of a dimension, from what the game has loaded and
 * from the files under the data folder, such as `minecraft:scoreboard` for
 * `data/minecraft/scoreboard.dat`.
 */
internal fun MinecraftServer.savedDataIds(dimension: String?): List<String> {
    val storage = storageOf(dimension)
    val loaded = storage.cache.filterValues { it.isPresent }.keys.map { it.id().toString() }
    return (loaded + fileIds(storage.dataFolder)).distinct().sorted()
}

/**
 * The saved data of an id as SNBT, or the tags that an NBT path matches. Data the game has loaded
 * comes from memory, since its file is only written at the next save.
 */
internal fun MinecraftServer.savedDataNbt(dimension: String?, id: String, path: String?): String {
    val storage = storageOf(dimension)
    val identifier = Identifier.tryParse(id) ?: invalidInput("Invalid saved data id: $id")
    val data =
        loadedData(storage, identifier)
            ?: fileData(storage.dataFolder, identifier)
            ?: notFound("No saved data $id. Leave out id to list the ids.")
    return if (path.isNullOrBlank()) data.toString() else tagsAt(data, path)
}

private fun MinecraftServer.storageOf(dimension: String?): SavedDataStorageAccessor {
    val storage = if (dimension.isNullOrBlank()) dataStorage else level(dimension).dataStorage
    return storage as SavedDataStorageAccessor
}

private fun MinecraftServer.loadedData(
    storage: SavedDataStorageAccessor,
    identifier: Identifier,
): CompoundTag? {
    val (type, data) =
        storage.cache.entries.firstOrNull { it.key.id() == identifier && it.value.isPresent }
            ?: return null
    return compound(encode(type, data.get()))
}

@Suppress("UNCHECKED_CAST")
private fun MinecraftServer.encode(type: SavedDataType<*>, data: SavedData): Tag {
    val ops = registryAccess().createSerializationContext(NbtOps.INSTANCE)
    return (type as SavedDataType<SavedData>).codec().encodeStart(ops, data).getOrThrow()
}

private fun fileData(folder: Path, identifier: Identifier): CompoundTag? {
    val file = identifier.withSuffix(DATA_SUFFIX).resolveAgainst(folder).normalize()
    // Ids may hold "..", which must not reach files outside the data folder.
    if (!file.startsWith(folder.normalize()) || !file.isRegularFile()) return null

    val tag =
        try {
            readNbt(file)
        } catch (exception: IOException) {
            failedPrecondition("Cannot read ${file.name}: ${exception.message}")
        }
    return compound(tag.get(DATA_KEY) ?: tag)
}

// Most saved data files are compressed, and some mods write them plain.
private fun readNbt(file: Path): CompoundTag =
    runCatching { NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()) }.getOrNull()
        ?: NbtIo.read(file)
        ?: failedPrecondition("${file.name} holds no NBT")

private fun compound(tag: Tag): CompoundTag =
    tag as? CompoundTag ?: CompoundTag().apply { put(DATA_KEY, tag) }

// A file at data/<namespace>/<path>.dat holds the saved data of <namespace>:<path>.
private fun fileIds(folder: Path): List<String> {
    if (!Files.isDirectory(folder)) return emptyList()
    return Files.walk(folder).use { paths ->
        paths
            .filter { it.isRegularFile() && it.name.endsWith(DATA_SUFFIX) }
            .map { folder.relativize(it) }
            .filter { it.nameCount >= 2 }
            .map { relative ->
                val path = relative.subpath(1, relative.nameCount).joinToString("/")
                "${relative.getName(0)}:${path.removeSuffix(DATA_SUFFIX)}"
            }
            .toList()
    }
}
