package top.madkarma.conduit.service.entity

import com.mojang.brigadier.StringReader
import kotlinx.serialization.Serializable
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.*

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
    /** The UUID of the only player to list, for a caller limited to their own data. */
    val onlyPlayer: String? = null,
)

internal fun MinecraftServer.findEntities(query: EntityQuery): List<EntityInfo> {
    val found =
        if (query.selector != null)
            select(query).filter { isVisible(it, query.onlyPlayer) }.take(query.limit)
        else EntityFilter(this, query).collect()

    return found.map { it.toInfo() }
}

/** Hides every player but onlyPlayer from a caller limited to that player's data. */
internal fun isVisible(entity: Entity, onlyPlayer: String?): Boolean =
    onlyPlayer == null || entity !is Player || entity.stringUUID == onlyPlayer

/** Finds an entity the caller may see, and treats hidden players as missing. */
internal fun MinecraftServer.visibleEntity(uuid: String, onlyPlayer: String?): Entity {
    val entity = entity(uuid)
    if (!isVisible(entity, onlyPlayer)) notFound("Entity not found: $uuid")
    return entity
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
            .filter { matches(it) && isVisible(it, query.onlyPlayer) }
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
