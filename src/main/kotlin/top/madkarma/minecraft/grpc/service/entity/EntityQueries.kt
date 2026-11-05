package top.madkarma.minecraft.grpc.service.entity

import com.mojang.brigadier.StringReader
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.SummonCommand
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import top.madkarma.minecraft.grpc.game.level
import top.madkarma.minecraft.grpc.game.parseId
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.proto.ListEntitiesRequest
import top.madkarma.minecraft.grpc.proto.SpawnEntityRequest
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument

private const val UNLOADED_CHUNK_MESSAGE =
    "Chunk is not loaded; force-load it with WorldService.SetChunkForced or move a player nearby"

internal fun MinecraftServer.findEntities(request: ListEntitiesRequest): List<Entity> {
    val limit = if (request.limit > 0) request.limit else Int.MAX_VALUE
    if (request.hasSelector()) return select(request).take(limit)
    return EntityFilter(this, request).collect(limit)
}

internal fun MinecraftServer.spawnEntity(request: SpawnEntityRequest): Entity {
    val key = ResourceKey.create(Registries.ENTITY_TYPE, parseId(request.type))
    val type = registryAccess().lookupOrThrow(Registries.ENTITY_TYPE).get(key)
        .orElseGet { invalidArgument("Unknown entity type: ${request.type}") }
    val level = level(request.dimension)
    requireEntityTicking(level, request.position.toMc())
    val snbt = request.snbt
    val tag = if (snbt.isBlank()) CompoundTag() else TagParser.parseCompoundFully(snbt)
    val source = createCommandSourceStack().withLevel(level).withSuppressedOutput()
    val entity = SummonCommand.createEntity(
        source,
        type,
        request.position.toMc(),
        tag,
        request.initialize,
    )
    if (request.hasRotation()) rotate(entity, request.rotation.yaw, request.rotation.pitch)
    return entity
}

// Entities added to chunks that are not loaded are saved to disk and unloaded immediately.
internal fun requireEntityTicking(level: ServerLevel, position: Vec3) {
    if (level.isPositionEntityTicking(BlockPos.containing(position))) return
    failedPrecondition(UNLOADED_CHUNK_MESSAGE)
}

private fun MinecraftServer.select(request: ListEntitiesRequest): List<Entity> {
    var source = createCommandSourceStack()
    if (request.dimension.isNotBlank()) source = source.withLevel(level(request.dimension))
    if (request.hasCenter()) source = source.withPosition(request.center.toMc())
    return EntityArgument.entities().parse(
        StringReader(request.selector),
    ).findEntities(source).toList()
}

private class EntityFilter(
    private val server: MinecraftServer,
    private val request: ListEntitiesRequest,
) {
    private val types: Set<EntityType<*>> = request.typesList.map { id ->
        BuiltInRegistries.ENTITY_TYPE.getOptional(parseId(id)).orElseGet {
            invalidArgument("Unknown entity type: $id")
        }
    }.toSet()
    private val box: AABB? =
        if (request.hasBox()) AABB(request.box.min.toMc(), request.box.max.toMc()) else null
    private val center: Vec3? = if (request.hasCenter()) request.center.toMc() else null
    private val radiusSq = if (request.hasRadius()) request.radius * request.radius else null

    init {
        if (radiusSq != null && center == null) invalidArgument("radius requires center")
    }

    fun collect(limit: Int): List<Entity> {
        val dimension = request.dimension
        val levels = if (dimension.isBlank()) server.allLevels else listOf(server.level(dimension))
        return levels.asSequence()
            .flatMap { it.allEntities }
            .filter(::matches)
            .take(limit)
            .toList()
    }

    private fun matches(entity: Entity): Boolean = when {
        types.isNotEmpty() && entity.type !in types -> false
        box != null && !box.contains(entity.position()) -> false
        radiusSq != null && entity.position().distanceToSqr(center!!) > radiusSq -> false
        request.hasTag() && request.tag !in entity.entityTags() -> false
        else -> true
    }
}
