package top.madkarma.conduit.game

import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityProcessor
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput

/**
 * An entity that a recorded call spawned or removed. A removed entity keeps its data, so undo can
 * bring it back with the same UUID.
 */
class EntityChange
private constructor(
    val dimension: ResourceKey<Level>,
    private val uuid: UUID,
    private val removedData: CompoundTag?,
) {
    fun undo(server: MinecraftServer) {
        if (removedData == null) {
            server.allLevels.firstNotNullOfOrNull { it.getEntity(uuid) }?.discard()
            return
        }

        val level = server.getLevel(dimension) ?: return
        if (level.getEntity(uuid) != null) return

        val input =
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), removedData)
        EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, EntityProcessor.NOP)
            ?.let(level::addFreshEntity)
    }

    companion object {
        fun spawned(entity: Entity) = EntityChange(entity.level().dimension(), entity.uuid, null)

        /** Null for entities that cannot be saved, such as players. */
        fun removed(entity: Entity): EntityChange? {
            val output =
                TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING,
                    entity.level().registryAccess(),
                )
            if (!entity.saveAsPassenger(output)) return null

            // Removing an entity only drops its passengers off, so they must not come back twice.
            val data = output.buildResult().also { it.remove("Passengers") }
            return EntityChange(entity.level().dimension(), entity.uuid, data)
        }
    }
}
