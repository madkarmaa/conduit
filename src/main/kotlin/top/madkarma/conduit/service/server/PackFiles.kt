package top.madkarma.conduit.service.server

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import net.minecraft.SharedConstants
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.PackType
import net.minecraft.world.level.storage.LevelResource
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.notFound
import top.madkarma.conduit.game.parseId

private const val PACK_NAME = "conduit"

/** The id of the pack that Conduit writes, as the game's pack repository names it. */
const val CONDUIT_PACK_ID = "file/$PACK_NAME"

private val REGISTRY_PATH = Regex("[a-z0-9_]+(/[a-z0-9_]+)*")

enum class PackFileType(val folder: String, val extension: String) {
    RECIPE("recipe", "json"),
    LOOT_TABLE("loot_table", "json"),
    FUNCTION("function", "mcfunction"),
    ADVANCEMENT("advancement", "json"),
    PREDICATE("predicate", "json"),
    ITEM_MODIFIER("item_modifier", "json"),
    TAG("tags", "json"),
}

/** One file of the Conduit pack. Tags also name their registry, such as "block" or "item". */
class PackFile(val type: PackFileType, val id: String, val registry: String? = null)

internal fun MinecraftServer.packRoot(): Path =
    getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME)

/** The file's place in the pack. Only valid ids and registries reach a path, so none leaves it. */
internal fun MinecraftServer.pathOf(file: PackFile): Path {
    val id = parseId(file.id)
    if (id.path.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
        invalidInput("Invalid id: ${file.id}")
    }

    val folder =
        if (file.type != PackFileType.TAG) file.type.folder
        else
            "tags/" +
                (file.registry?.takeIf { REGISTRY_PATH.matches(it) }
                    ?: invalidInput("Tags need a registry, such as \"block\""))
    return packRoot().resolve("data/${id.namespace}/$folder/${id.path}.${file.type.extension}")
}

internal fun MinecraftServer.listPackFiles(): List<String> {
    val data = packRoot().resolve("data")
    if (!Files.isDirectory(data)) return emptyList()

    return Files.walk(data).use { paths ->
        paths
            .filter { it.isRegularFile() && it.extension in setOf("json", "mcfunction") }
            .map { packRoot().relativize(it).toString() }
            .sorted()
            .toList()
    }
}

internal fun MinecraftServer.readPackFile(file: PackFile): String {
    val path = pathOf(file)
    if (!Files.isRegularFile(path)) notFound("No such file in the Conduit pack: ${file.id}")
    return Files.readString(path)
}

/** Writes a file and the pack's metadata, which the game needs to find the pack. */
internal fun MinecraftServer.writePackFile(file: PackFile, content: String) {
    val path = pathOf(file)
    Files.createDirectories(path.parent)
    Files.writeString(path, content)

    val metadata = packRoot().resolve("pack.mcmeta")
    if (!Files.exists(metadata)) Files.writeString(metadata, packMetadata())
}

internal fun MinecraftServer.deletePackFile(file: PackFile) {
    if (!Files.deleteIfExists(pathOf(file)))
        notFound("No such file in the Conduit pack: ${file.id}")
}

private fun packMetadata(): String {
    val format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)
    val version =
        if (format.minor() == 0) "${format.major()}" else "[${format.major()}, ${format.minor()}]"
    return """
        {
            "pack": {
                "description": "Written by Conduit",
                "min_format": $version,
                "max_format": $version
            }
        }
    """
        .trimIndent() + "\n"
}
