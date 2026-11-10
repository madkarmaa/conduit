package top.madkarma.conduit.service.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.*
import top.madkarma.conduit.mcp.round

/**
 * Where a teleport put an entity: the block under its feet, whether it fits there, and whether it
 * went to the nearest spot where it fits instead of the target.
 */
class Landing(
    val entity: EntityInfo,
    val blockBelow: String,
    val free: Boolean,
    val moved: Boolean = false,
)

/** Moves an entity, players included, also across dimensions. Null keeps the dimension. */
internal fun MinecraftServer.teleportEntity(
    uuid: String,
    dimension: String?,
    position: Vector3,
    rotation: Rotation?,
): Landing {
    val entity = editedEntity(uuid)
    val level = if (dimension.isNullOrBlank()) entity.level() as ServerLevel else level(dimension)
    val yaw = rotation?.yaw ?: entity.yRot
    val pitch = rotation?.pitch ?: entity.xRot

    val target = position.toMc()
    if (entity !is ServerPlayer) requireEntityTicking(level, target)
    val destination = destinationFor(entity, level, target)

    if (
        !entity.teleportTo(
            level,
            destination.x,
            destination.y,
            destination.z,
            emptySet(),
            yaw,
            pitch,
            true,
        )
    ) {
        failedPrecondition("Teleport was rejected")
    }

    // The game recreates entities other than players when they change dimension.
    val moved = entity(uuid)
    val below = moved.level().getBlockState(BlockPos.containing(moved.x, moved.y - 0.01, moved.z))
    return Landing(
        moved.toInfo(),
        below.toInfo().serialized,
        moved.level().noCollision(moved),
        destination != target,
    )
}

// An entity dropped into blocks suffocates, so it goes to the nearest spot where it fits. In an
// unloaded chunk the blocks are unknown, and the target stays.
private fun destinationFor(entity: Entity, level: ServerLevel, target: Vec3): Vec3 {
    if (!level.isLoaded(BlockPos.containing(target))) return target
    return entity.fittingPosition(level, target)
        ?: failedPrecondition(
            "No room for ${entity.type.description.string} within 3 blocks of " +
                "${round(target.x)} ${round(target.y)} ${round(target.z)}; the entity stayed " +
                "where it was",
        )
}
