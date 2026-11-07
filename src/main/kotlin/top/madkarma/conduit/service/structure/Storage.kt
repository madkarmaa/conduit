package top.madkarma.conduit.service.structure

import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.IdentifierException
import net.minecraft.resources.Identifier
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.notFound
import top.madkarma.conduit.game.parseId

/**
 * Saves a region, corners inclusive, to the world's structure storage, the same storage structure
 * blocks use. The ignored block, such as "minecraft:structure_void", is left out.
 */
internal suspend fun saveStructure(
    templateId: String,
    dimension: String?,
    from: BlockCoordinates,
    to: BlockCoordinates,
    includeEntities: Boolean,
    ignoreBlock: String?,
) {
    val id = parseId(templateId)
    val manager = templateManager()
    val path = worldStructurePath(manager, id)

    val template = GameServer.run {
        it.captureStructure(dimension, from, to, includeEntities, ignoreBlock)
    }

    withContext(Dispatchers.IO) { writeStructureFile(path, template) }

    // The manager caches loaded templates, which would hide the new file from later placements.
    manager.remove(id)
}

/** Places a template saved in the world or one from a datapack, including the vanilla ones. */
internal suspend fun placeStoredStructure(
    templateId: String,
    placement: StructurePlacement,
): PlacementResult {
    val id = parseId(templateId)
    checkPlacement(placement)

    val manager = templateManager()
    val template =
        withContext(Dispatchers.IO) { manager.get(id).getOrNull() }
            ?: notFound("Unknown structure: $id")

    return GameServer.run { it.placeStructure(template, placement) }
}

/** Ids of the templates saved in the world and those from enabled datapacks, sorted. */
internal suspend fun listStructures(): List<String> {
    val manager = templateManager()
    return withContext(Dispatchers.IO) {
        manager.listTemplates().map { it.toString() }.sorted().toList()
    }
}

// World generation loads templates on worker threads too, so the manager is safe to use from here.
private fun templateManager(): StructureTemplateManager =
    GameServer.require().structureTemplateManager

private fun worldStructurePath(manager: StructureTemplateManager, id: Identifier): Path =
    try {
        manager
            .worldTemplates()
            .createAndValidatePathToStructure(id, StructureTemplateManager.WORLD_STRUCTURE_LISTER)
    } catch (exception: IdentifierException) {
        invalidInput("Invalid structure id: ${exception.message}")
    }
