package top.madkarma.conduit.service.structure

import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.IdentifierException
import net.minecraft.resources.Identifier
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.notFound
import top.madkarma.conduit.game.parseId
import top.madkarma.conduit.proto.*

internal suspend fun saveStructure(request: SaveStructureRequest) {
    val id = parseId(request.id)
    val manager = templateManager()
    val path = worldStructurePath(manager, id)

    val template = GameServer.run {
        it.captureStructure(
            request.dimension,
            request.from,
            request.to,
            request.includeEntities,
            request.ignoreBlock,
        )
    }

    withContext(Dispatchers.IO) { writeStructureFile(path, template) }

    // The manager caches loaded templates, which would hide the new file from later placements.
    manager.remove(id)
}

internal suspend fun placeStoredStructure(request: PlaceStructureRequest): ImportStructureResponse {
    val id = parseId(request.id)
    checkPlacement(request.placement)

    val manager = templateManager()
    val template =
        withContext(Dispatchers.IO) { manager.get(id).getOrNull() }
            ?: notFound("Unknown structure: $id")

    return GameServer.run { it.placeStructure(template, request.placement) }
}

internal suspend fun listStructures(): StructureList {
    val manager = templateManager()
    val ids =
        withContext(Dispatchers.IO) {
            manager.listTemplates().map { it.toString() }.sorted().toList()
        }

    return structureList { this.ids += ids }
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
