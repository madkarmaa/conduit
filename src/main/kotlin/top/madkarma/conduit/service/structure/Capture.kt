package top.madkarma.conduit.service.structure

import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.parseId

internal fun MinecraftServer.captureStructure(
    dimension: String?,
    from: BlockCoordinates,
    to: BlockCoordinates,
    includeEntities: Boolean,
    ignoreBlock: String?,
): StructureTemplate {
    val level = level(dimension)
    val ignoredBlocks = ignoredBlocks(ignoreBlock)
    val min = BlockPos(min(from.x, to.x), min(from.y, to.y), min(from.z, to.z))
    val max = BlockPos(max(from.x, to.x), max(from.y, to.y), max(from.z, to.z))

    // This computes spans as longs, since opposite ends of the world overflow an int.
    checkVolume((max.x - min.x + 1L) * (max.y - min.y + 1L) * (max.z - min.z + 1L))

    val size = Vec3i(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1)
    val template = StructureTemplate()

    // The template holds copies of the blocks and entities, so a worker thread can turn it into
    // NBT off the server thread.
    template.fillFromWorld(level, min, size, includeEntities, ignoredBlocks)
    return template
}

internal fun checkVolume(volume: Long) {
    val limit = Conduit.gameConfig.maxStructureVolume
    if (volume > limit) invalidInput("Structure of $volume blocks exceeds the limit of $limit")
}

internal val Vec3i.volume: Long
    get() = x.toLong() * y * z

private fun ignoredBlocks(id: String?): List<Block> {
    if (id.isNullOrBlank()) return emptyList()

    val block = BuiltInRegistries.BLOCK.getOptional(parseId(id)).getOrNull()
    return listOf(block ?: invalidInput("Unknown block: $id"))
}
