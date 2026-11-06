package top.madkarma.conduit.service.structure

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtUtils
import net.minecraft.server.MinecraftServer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import top.madkarma.conduit.rpc.invalidArgument
import top.madkarma.conduit.rpc.toStatusException

// The game's template loader assumes this version for files saved before DataVersion existed.
private const val UNVERSIONED_DATA_VERSION = 500

internal fun encodeStructure(template: StructureTemplate): ByteArray {
    val output = ByteArrayOutputStream()
    NbtIo.writeCompressed(template.save(CompoundTag()), output)
    return output.toByteArray()
}

/**
 * Reads a structure file like the game's template loader does, upgrading files from older game
 * versions. It is safe off the server thread, because the game runs the data fixer on worker
 * threads too and the registries are frozen.
 */
internal fun MinecraftServer.decodeStructure(data: ByteArray): StructureTemplate {
    val template =
        try {
            readTemplate(data)
        } catch (exception: Exception) {
            invalidArgument("Invalid structure data: ${exception.message}")
        }

    val size = template.size
    if (size.x < 1 || size.y < 1 || size.z < 1) invalidArgument("The data is not a structure")

    return template
}

internal fun writeStructureFile(path: Path, template: StructureTemplate) {
    try {
        Files.createDirectories(path.parent)
        NbtIo.writeCompressed(template.save(CompoundTag()), path)
    } catch (exception: IOException) {
        throw exception.toStatusException()
    }
}

private fun MinecraftServer.readTemplate(data: ByteArray): StructureTemplate {
    val nbt = NbtIo.readCompressed(ByteArrayInputStream(data), NbtAccounter.unlimitedHeap())
    val version = NbtUtils.getDataVersion(nbt, UNVERSIONED_DATA_VERSION)
    val upgraded = DataFixTypes.STRUCTURE.updateToCurrentVersion(fixerUpper, nbt, version)
    val blocks =
        registryAccess().lookupOrThrow(Registries.BLOCK).filterFeatures(worldData.enabledFeatures())

    val template = StructureTemplate()
    template.load(blocks, upgraded)
    return template
}
