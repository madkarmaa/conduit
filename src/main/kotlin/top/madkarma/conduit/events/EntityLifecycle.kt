package top.madkarma.conduit.events

import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import top.madkarma.conduit.game.EntityInfo
import top.madkarma.conduit.game.toInfo

// Item entities and experience orbs come and go constantly, and player events already cover
// players.
private val IGNORED_TYPES = setOf("minecraft:item", "minecraft:experience_orb", "minecraft:player")

/** A new entity was added to the world. Entities that load with their chunk are not reported. */
@Serializable
data class EntitySpawnEvent(
    val entity: EntityInfo,
    /**
     * How a mob or other entity type was created, e.g. "natural", "breeding" or "command". Not set
     * for entities the game creates directly, such as projectiles.
     */
    val spawnReason: String?,
)

/** Publishes ENTITY_SPAWN. */
object EntityLifecycle {
    // Called from the fresh entity mixin, only for entities that were actually added.
    @JvmStatic
    fun onEntitySpawned(entity: Entity) {
        if (!EventBus.wants()) return
        if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() in IGNORED_TYPES) return

        EventBus.publish(EventType.ENTITY_SPAWN) {
            EntitySpawnEvent(entity.toInfo(), entity.spawnReason()?.name?.lowercase())
        }
    }
}
