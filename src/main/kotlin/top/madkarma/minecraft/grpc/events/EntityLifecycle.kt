package top.madkarma.minecraft.grpc.events

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.*

/** Publishes EVENT_TYPE_ENTITY_SPAWN and EVENT_TYPE_ENTITY_REMOVE. */
object EntityLifecycle {
    fun register() {
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (!EventBus.wants(EventType.EVENT_TYPE_ENTITY_REMOVE)) return@register

            val type = typeOf(entity)
            EventBus.publish(
                EventType.EVENT_TYPE_ENTITY_REMOVE,
                accepts = { it.wantsEntityType(type) },
            ) {
                entityRemove = removePayload(entity)
            }
        }
    }

    // Called from the fresh entity mixin, only for entities that were actually added.
    @JvmStatic
    fun onEntitySpawned(entity: Entity) {
        if (!EventBus.wants(EventType.EVENT_TYPE_ENTITY_SPAWN)) return

        val type = typeOf(entity)
        EventBus.publish(
            EventType.EVENT_TYPE_ENTITY_SPAWN,
            accepts = { it.wantsEntityType(type) },
        ) {
            entitySpawn = spawnPayload(entity)
        }
    }

    private fun typeOf(entity: Entity): String =
        BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()

    private fun spawnPayload(entity: Entity): EntitySpawnEvent = entitySpawnEvent {
        this.entity = entity.toProto()
        entity.spawnReason()?.let { spawnReason = it.name.lowercase() }
    }

    private fun removePayload(entity: Entity): EntityRemoveEvent = entityRemoveEvent {
        this.entity = entity.toProto()
        reason = removalReasonOf(entity)
    }

    // The game stops tracking entities as soon as their chunk becomes inaccessible, and only sets
    // UNLOADED_TO_CHUNK later when it saves the chunk, so a missing reason means the same thing.
    private fun removalReasonOf(entity: Entity): RemovalReason =
        when (entity.removalReason) {
            Entity.RemovalReason.KILLED -> RemovalReason.REMOVAL_REASON_KILLED
            Entity.RemovalReason.DISCARDED -> RemovalReason.REMOVAL_REASON_DISCARDED
            Entity.RemovalReason.UNLOADED_TO_CHUNK,
            null -> RemovalReason.REMOVAL_REASON_UNLOADED_TO_CHUNK
            Entity.RemovalReason.UNLOADED_WITH_PLAYER ->
                RemovalReason.REMOVAL_REASON_UNLOADED_WITH_PLAYER
            Entity.RemovalReason.CHANGED_DIMENSION -> RemovalReason.REMOVAL_REASON_CHANGED_DIMENSION
        }
}
