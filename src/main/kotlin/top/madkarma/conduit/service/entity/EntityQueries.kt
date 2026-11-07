package top.madkarma.conduit.service.entity

import com.mojang.brigadier.StringReader
import kotlinx.serialization.Serializable
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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.*

private const val UNLOADED_CHUNK_MESSAGE =
    "Chunk is not loaded; force-load it with the force_chunk action of world_settings or move " +
        "a player nearby"

@Serializable data class Box(val min: Vector3, val max: Vector3)

data class EntityQuery(
    /** Null means every dimension. */
    val dimension: String? = null,
    /** Entity type ids, such as "minecraft:zombie". Empty means any type. */
    val types: List<String> = emptyList(),
    val box: Box? = null,
    /** Distance filter around center. */
    val center: Vector3? = null,
    val radius: Double? = null,
    /** An entity selector such as "@e[type=cow,limit=5]". Overrides the other filters. */
    val selector: String? = null,
    /** Required scoreboard tag. */
    val tag: String? = null,
    val limit: Int = Int.MAX_VALUE,
)

data class SpawnRequest(
    val type: String,
    val dimension: String?,
    val position: Vector3,
    val rotation: Rotation? = null,
    /** Applied on spawn, like the /summon nbt argument. */
    val snbt: String? = null,
    /** Runs the natural spawn setup, such as equipment and variants, like /summon without nbt. */
    val initialize: Boolean = true,
)

internal fun MinecraftServer.findEntities(query: EntityQuery): List<EntityInfo> {
    val found =
        if (query.selector != null) select(query).take(query.limit)
        else EntityFilter(this, query).collect()

    return found.map { it.toInfo() }
}

internal fun MinecraftServer.describeEntity(uuid: String): EntityInfo = entity(uuid).toInfo()

internal fun MinecraftServer.spawnEntity(request: SpawnRequest): EntityInfo {
    val key = ResourceKey.create(Registries.ENTITY_TYPE, parseId(request.type))

    val type =
        registryAccess().lookupOrThrow(Registries.ENTITY_TYPE).get(key).orElseGet {
            invalidInput("Unknown entity type: ${request.type}")
        }

    val level = level(request.dimension)
    requireEntityTicking(level, request.position.toMc())

    val snbt = request.snbt.orEmpty()
    val tag = if (snbt.isBlank()) CompoundTag() else TagParser.parseCompoundFully(snbt)
    val source = createCommandSourceStack().withLevel(level).withSuppressedOutput()
    val entity =
        SummonCommand.createEntity(source, type, request.position.toMc(), tag, request.initialize)

    request.rotation?.let { rotate(entity, it.yaw, it.pitch) }

    // Spawn setup can lower the max health without lowering the health, since horses get a random
    // max health. The entity's first tick would lower the health to match, so this does it now.
    if (entity is LivingEntity && entity.health > entity.maxHealth) entity.health = entity.maxHealth

    return entity.toInfo()
}

// The game saves entities added to unloaded chunks to disk and unloads them right away.
internal fun requireEntityTicking(level: ServerLevel, position: Vec3) {
    if (level.isPositionEntityTicking(BlockPos.containing(position))) return
    failedPrecondition(UNLOADED_CHUNK_MESSAGE)
}

private fun MinecraftServer.select(query: EntityQuery): List<Entity> {
    var source = createCommandSourceStack()

    if (query.dimension != null) source = source.withLevel(level(query.dimension))
    if (query.center != null) source = source.withPosition(query.center.toMc())

    return EntityArgument.entities()
        .parse(StringReader(query.selector))
        .findEntities(source)
        .toList()
}

private class EntityFilter(private val server: MinecraftServer, private val query: EntityQuery) {
    private val types: Set<EntityType<*>> =
        query.types
            .map { id ->
                BuiltInRegistries.ENTITY_TYPE.getOptional(parseId(id)).orElseGet {
                    invalidInput("Unknown entity type: $id")
                }
            }
            .toSet()

    private val box: AABB? = query.box?.let { AABB(it.min.toMc(), it.max.toMc()) }

    private val center: Vec3? = query.center?.toMc()

    private val radiusSq = query.radius?.let { it * it }

    init {
        if (radiusSq != null && center == null) invalidInput("radius requires center")
    }

    fun collect(): List<Entity> {
        val levels =
            if (query.dimension == null) server.allLevels else listOf(server.level(query.dimension))

        return levels
            .asSequence()
            .flatMap { it.allEntities }
            .filter(::matches)
            .take(query.limit)
            .toList()
    }

    private fun matches(entity: Entity): Boolean =
        when {
            types.isNotEmpty() && entity.type !in types -> false
            box != null && !box.contains(entity.position()) -> false
            radiusSq != null && entity.position().distanceToSqr(center!!) > radiusSq -> false
            query.tag != null && query.tag !in entity.entityTags() -> false
            else -> true
        }
}
