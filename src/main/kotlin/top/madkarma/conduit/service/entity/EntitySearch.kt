package top.madkarma.conduit.service.entity

import com.mojang.brigadier.StringReader
import kotlinx.serialization.Serializable
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.*

@Serializable data class Box(val min: Vector3, val max: Vector3)

data class EntityQuery(
    /** Null means every dimension. */
    val dimension: String? = null,
    /** Entity type ids, such as "minecraft:zombie". Empty means any type. */
    val types: List<String> = emptyList(),
    /** Mob categories, such as "monster". Empty means any category. */
    val categories: List<String> = emptyList(),
    val box: Box? = null,
    /** Distance filter around center, which also sorts the results nearest first. */
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

/** An entity in a list, with where it is relative to the search center and to the surface. */
@Serializable
data class ListedEntity(
    val entity: EntityInfo,
    /** Blocks from the center of the search, when it has one. */
    val distance: Double? = null,
    /**
     * Blocks from the surface of the entity's column down to its feet, 0 or less above ground. Null
     * under a ceiling, where the surface is the roof.
     */
    val belowSurface: Int? = null,
)

internal fun MinecraftServer.findEntities(query: EntityQuery): List<ListedEntity> {
    val found =
        if (query.selector != null)
            select(query).filter { isVisible(it, query.onlyPlayer) }.take(query.limit)
        else EntityFilter(this, query).collect()

    val center = query.center?.toMc()
    return found.map { it.toListed(center) }
}

private fun Entity.toListed(center: Vec3?): ListedEntity {
    val distance = center?.let { position().distanceTo(it) }
    if (level().dimensionType().hasCeiling()) return ListedEntity(toInfo(), distance)

    val surface = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ)
    return ListedEntity(toInfo(), distance, surface - blockY)
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

    private val categories: Set<MobCategory> =
        query.categories
            .map { name ->
                MobCategory.entries.find { it.serializedName == name }
                    ?: invalidInput("Unknown mob category: $name")
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

        val matching =
            levels
                .asSequence()
                .flatMap { it.allEntities }
                .filter { matches(it) && isVisible(it, query.onlyPlayer) }
        val ordered =
            if (center == null) matching
            else matching.sortedBy { it.position().distanceToSqr(center) }
        return ordered.take(query.limit).toList()
    }

    private fun matches(entity: Entity): Boolean =
        when {
            types.isNotEmpty() && entity.type !in types -> false
            categories.isNotEmpty() && entity.type.category !in categories -> false
            box != null && !box.contains(entity.position()) -> false
            radiusSq != null && entity.position().distanceToSqr(center!!) > radiusSq -> false
            query.tag != null && query.tag !in entity.entityTags() -> false
            else -> true
        }
}
