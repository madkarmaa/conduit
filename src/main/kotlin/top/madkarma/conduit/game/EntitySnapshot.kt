package top.madkarma.conduit.game

import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityProcessor
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput

/**
 * An entity other than a player before a recorded call changed it: its full data, its dimension and
 * what it rides. Undo puts all of it back, and brings the entity back when it is gone by then.
 */
class EntitySnapshot
private constructor(
    private val server: MinecraftServer,
    override val subject: UUID,
    val dimension: ResourceKey<Level>,
    private val data: CompoundTag,
    private val vehicle: UUID?,
    // Some changes only show on later ticks, such as a walk along a path.
    private val changesLater: Boolean,
) : UndoableChange {
    override fun changed(): Boolean {
        if (changesLater) return true
        val now = server.findEntity(subject)?.let { of(it) } ?: return true
        return now.dimension != dimension || now.data != data || now.vehicle != vehicle
    }

    override fun undo(server: MinecraftServer) {
        val found = server.findEntity(subject)
        // A dying entity is removed once its death ends, whatever its data says by then.
        if (found != null && !found.isAlive) found.discard()
        val entity = found?.takeIf { it.isAlive }?.let(::moveBack) ?: return bringBack()

        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), data))
        (entity as? Mob)?.navigation?.stop()

        if (entity.vehicle?.uuid == vehicle) return
        entity.stopRiding()
        vehicle?.let(server::findEntity)?.let { entity.startRiding(it, true, true) }
    }

    // The data holds the position, but only a teleport changes the dimension. The game recreates
    // entities that change dimension, with the same UUID.
    private fun moveBack(entity: Entity): Entity? {
        if (entity.level().dimension() == dimension) return entity

        val level = server.getLevel(dimension) ?: return null
        entity.teleportTo(level, entity.x, entity.y, entity.z, emptySet(), 0f, 0f, true)
        return server.findEntity(subject)
    }

    private fun bringBack() {
        val level = server.getLevel(dimension) ?: return
        val input = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), data)
        EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, EntityProcessor.NOP)
            ?.let(level::addFreshEntity)
    }

    companion object {
        fun of(entity: Entity, changesLater: Boolean = false): EntitySnapshot {
            val output =
                TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING,
                    entity.level().registryAccess(),
                )
            entity.saveWithoutId(output)

            // Passengers have snapshots of their own, and the id lets undo bring the entity back.
            val data = output.buildResult().also { it.remove("Passengers") }
            data.putString("id", EntityType.getKey(entity.type).toString())

            return EntitySnapshot(
                (entity.level() as ServerLevel).server,
                entity.uuid,
                entity.level().dimension(),
                data,
                entity.vehicle?.uuid,
                changesLater,
            )
        }
    }
}
