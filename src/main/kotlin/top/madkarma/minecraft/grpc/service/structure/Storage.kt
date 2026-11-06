package top.madkarma.minecraft.grpc.service.structure

import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import net.minecraft.IdentifierException
import net.minecraft.resources.Identifier
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.game.parseId
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.notFound
import top.madkarma.minecraft.grpc.rpc.runInWorkerThread

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

    runInWorkerThread { writeStructureFile(path, template) }

    // The manager caches loaded templates, which would hide the new file from later placements.
    manager.remove(id)
}

internal suspend fun placeStoredStructure(request: PlaceStructureRequest): ImportStructureResponse {
    val id = parseId(request.id)
    checkPlacement(request.placement)

    val manager = templateManager()
    val template =
        runInWorkerThread { manager.get(id).getOrNull() } ?: notFound("Unknown structure: $id")

    return GameServer.run { it.placeStructure(template, request.placement) }
}

internal suspend fun listStructures(): StructureList {
    val manager = templateManager()
    val ids = runInWorkerThread {
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
        invalidArgument("Invalid structure id: ${exception.message}")
    }
