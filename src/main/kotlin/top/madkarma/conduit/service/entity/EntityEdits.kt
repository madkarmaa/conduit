package top.madkarma.conduit.service.entity

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
import top.madkarma.conduit.proto.*
import top.madkarma.conduit.rpc.failedPrecondition

internal fun MinecraftServer.removeEntity(request: RemoveEntityRequest) {
    val entity = entity(request.uuid)

    when {
        request.kill -> entity.kill(entity.level() as ServerLevel)

        entity is ServerPlayer ->
            failedPrecondition(
                "Players cannot be discarded; use kill or PlayerService.Kick",
            )

        else -> entity.discard()
    }
}

internal fun MinecraftServer.teleportEntity(request: TeleportRequest): EntityInfo {
    val entity = entity(request.uuid)
    val current = entity.level() as ServerLevel
    val level = if (request.dimension.isBlank()) current else level(request.dimension)
    val yaw = if (request.hasRotation()) request.rotation.yaw else entity.yRot
    val pitch = if (request.hasRotation()) request.rotation.pitch else entity.xRot
    val position = request.position

    if (entity !is ServerPlayer) requireEntityTicking(level, position.toMc())

    if (
        !entity.teleportTo(
            level,
            position.x,
            position.y,
            position.z,
            emptySet(),
            yaw,
            pitch,
            true,
        )
    ) {
        failedPrecondition("Teleport was rejected")
    }

    // Entities other than players are recreated when they change dimension.
    return entity(request.uuid).toProto()
}

internal fun MinecraftServer.changeVelocity(request: SetVelocityRequest) {
    val entity = entity(request.uuid)
    val velocity = request.velocity.toMc()

    entity.deltaMovement = if (request.add) entity.deltaMovement.add(velocity) else velocity
    entity.syncVelocity = true
}

internal fun MinecraftServer.changeRotation(request: SetRotationRequest) {
    rotate(entity(request.uuid), request.rotation.yaw, request.rotation.pitch)
}

internal fun rotate(entity: Entity, yaw: Float, pitch: Float) {
    if (entity is ServerPlayer) {
        // Players own their rotation on the client, so it is sent as a relative teleport that
        // keeps the position.
        entity.teleportTo(
            entity.level(),
            0.0,
            0.0,
            0.0,
            setOf(Relative.X, Relative.Y, Relative.Z),
            yaw,
            pitch,
            true,
        )

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

internal fun MinecraftServer.entityNbt(request: EntityRef): Nbt = nbt {
    snbt = saveEntity(entity(request.uuid)).toString()
}

internal fun MinecraftServer.mergeEntityNbt(request: MergeNbtRequest) {
    val entity = entity(request.uuid)
    if (entity is ServerPlayer) failedPrecondition("Player data is read-only; use PlayerService")

    val merged = saveEntity(entity).merge(TagParser.parseCompoundFully(request.snbt))
    merged.store("UUID", UUIDUtil.CODEC, entity.uuid)

    entity.load(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), merged))
}

internal fun MinecraftServer.changeCustomName(request: SetCustomNameRequest) {
    val entity = entity(request.uuid)

    entity.customName = if (request.hasName()) request.name.toComponent(registryAccess()) else null
    entity.isCustomNameVisible = request.visible
}

internal fun MinecraftServer.changeTags(request: SetTagsRequest) {
    val entity = entity(request.uuid)

    request.removeList.forEach { entity.removeTag(it) }
    request.addList.forEach { entity.addTag(it) }
}

internal fun MinecraftServer.mount(request: MountRequest) {
    val passenger = entity(request.passenger)

    if (!passenger.startRiding(entity(request.vehicle), true, true)) {
        failedPrecondition("Entity cannot ride ${request.vehicle}")
    }
}

internal fun MinecraftServer.dismount(request: EntityRef) {
    entity(request.uuid).stopRiding()
}
