package top.madkarma.conduit.service.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import top.madkarma.conduit.game.*

/** Removes an entity without a trace, or kills it, with loot and death effects. */
internal fun MinecraftServer.removeEntity(uuid: String, kill: Boolean) {
    val entity = entity(uuid)
    if (entity is ServerPlayer && !kill) {
        failedPrecondition("Players cannot be removed; kill them or use the kick action")
    }

    EditJournal.beforeRemove(entity)
    if (kill) entity.kill(entity.level() as ServerLevel) else entity.discard()
}

/** Where a teleport put an entity: the block under its feet and whether it fits there. */
class Landing(val entity: EntityInfo, val blockBelow: String, val free: Boolean)

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

    if (entity !is ServerPlayer) requireEntityTicking(level, position.toMc())

    if (
        !entity.teleportTo(level, position.x, position.y, position.z, emptySet(), yaw, pitch, true)
    ) {
        failedPrecondition("Teleport was rejected")
    }

    // The game recreates entities other than players when they change dimension.
    val moved = entity(uuid)
    val below = moved.level().getBlockState(BlockPos.containing(moved.x, moved.y - 0.01, moved.z))
    return Landing(moved.toInfo(), below.toInfo().serialized, moved.level().noCollision(moved))
}

/** Sets the velocity in blocks per tick, or adds to the current velocity. */
internal fun MinecraftServer.changeVelocity(uuid: String, velocity: Vector3, add: Boolean) {
    val entity = editedEntity(uuid)
    val change = velocity.toMc()

    entity.deltaMovement = if (add) entity.deltaMovement.add(change) else change
    entity.syncVelocity = true
}

internal fun MinecraftServer.changeRotation(uuid: String, rotation: Rotation) {
    rotate(editedEntity(uuid), rotation.yaw, rotation.pitch)
}

internal fun rotate(entity: Entity, yaw: Float, pitch: Float) {
    if (entity is ServerPlayer) {
        // The client controls a player's rotation, so the server sends it as a relative teleport
        // that keeps the position.
        val relative = setOf(Relative.X, Relative.Y, Relative.Z)
        entity.teleportTo(entity.level(), 0.0, 0.0, 0.0, relative, yaw, pitch, true)
        return
    }

    entity.yRot = yaw
    entity.xRot = pitch
    entity.yHeadRot = yaw

    if (entity is LivingEntity) entity.yBodyRot = yaw
}

internal fun MinecraftServer.saveEntity(entity: Entity): CompoundTag {
    val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess())
    entity.saveWithoutId(output)
    return output.buildResult()
}

/** The entity's complete data as SNBT. */
internal fun MinecraftServer.entityNbt(uuid: String): String = saveEntity(entity(uuid)).toString()

/**
 * Merges SNBT into the entity data, like /data merge entity. The game skips data it cannot read,
 * such as a broken villager trade, so such a merge puts the old data back and fails instead.
 */
internal fun MinecraftServer.mergeEntityNbt(uuid: String, snbt: String) {
    val entity = editedEntity(uuid)
    if (entity is ServerPlayer) failedPrecondition("Player data is read-only here")

    val original = saveEntity(entity)
    val merged = original.copy().merge(TagParser.parseCompoundFully(snbt))
    merged.store("UUID", UUIDUtil.CODEC, entity.uuid)

    val problems = ProblemReporter.Collector()
    entity.load(TagValueInput.create(problems, registryAccess(), merged))
    if (problems.isEmpty) return

    entity.load(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), original))
    invalidInput("Nothing changed, since the game cannot read this data: ${problems.report}")
}

/** A null name clears the custom name. */
internal fun MinecraftServer.changeCustomName(uuid: String, name: String?, visible: Boolean) {
    val entity = editedEntity(uuid)

    entity.customName = name?.let { textComponent(it, registryAccess()) }
    entity.isCustomNameVisible = visible
}

internal fun MinecraftServer.changeTags(uuid: String, add: List<String>, remove: List<String>) {
    val entity = editedEntity(uuid)

    remove.forEach { entity.removeTag(it) }
    add.forEach { entity.addTag(it) }
}

internal fun MinecraftServer.mount(passenger: String, vehicle: String) {
    if (!editedEntity(passenger).startRiding(entity(vehicle), true, true)) {
        failedPrecondition("Entity cannot ride $vehicle")
    }
}

internal fun MinecraftServer.dismount(uuid: String) {
    editedEntity(uuid).stopRiding()
}
