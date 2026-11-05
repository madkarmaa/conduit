package top.madkarma.minecraft.grpc.service.batch

import net.minecraft.server.MinecraftServer
import top.madkarma.minecraft.grpc.proto.Operation
import top.madkarma.minecraft.grpc.proto.Operation.OperationCase.*
import top.madkarma.minecraft.grpc.proto.OperationResult
import top.madkarma.minecraft.grpc.service.entity.*

/** Returns false when the operation is not an EntityService operation. */
internal fun MinecraftServer.applyEntityOperation(
    operation: Operation,
    result: OperationResult.Builder,
): Boolean {
    when (operation.operationCase) {
        LIST_ENTITIES -> result.entities = findEntities(operation.listEntities)
        GET_ENTITY -> result.entity = describeEntity(operation.getEntity)
        SPAWN_ENTITY -> result.entity = spawnEntity(operation.spawnEntity)
        REMOVE_ENTITY -> removeEntity(operation.removeEntity)
        TELEPORT -> result.entity = teleportEntity(operation.teleport)
        SET_VELOCITY -> changeVelocity(operation.setVelocity)
        SET_ROTATION -> changeRotation(operation.setRotation)
        GET_NBT -> result.nbt = entityNbt(operation.getNbt)
        MERGE_NBT -> mergeEntityNbt(operation.mergeNbt)
        SET_CUSTOM_NAME -> changeCustomName(operation.setCustomName)
        SET_TAGS -> changeTags(operation.setTags)
        DAMAGE -> damage(operation.damage)
        SET_HEALTH -> changeHealth(operation.setHealth)
        GET_EFFECTS -> result.effects = effectsOf(operation.getEffects)
        ADD_EFFECT -> addEffect(operation.addEffect)
        REMOVE_EFFECT -> removeEffect(operation.removeEffect)
        GET_ATTRIBUTES -> result.attributes = attributesOf(operation.getAttributes.uuid)
        SET_ATTRIBUTE_BASE -> changeAttributeBase(operation.setAttributeBase)
        MOUNT -> mount(operation.mount)
        DISMOUNT -> dismount(operation.dismount)
        else -> return false
    }

    return true
}
