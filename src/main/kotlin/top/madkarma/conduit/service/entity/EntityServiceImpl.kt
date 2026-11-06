package top.madkarma.conduit.service.entity

import com.google.protobuf.Empty
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.proto.*

private val EMPTY: Empty = Empty.getDefaultInstance()

class EntityServiceImpl : EntityServiceGrpcKt.EntityServiceCoroutineImplBase() {
    override suspend fun listEntities(request: ListEntitiesRequest): EntityList = GameServer.run {
        it.findEntities(request)
    }

    override suspend fun getEntity(request: EntityRef): EntityInfo = GameServer.run {
        it.describeEntity(request)
    }

    override suspend fun spawnEntity(request: SpawnEntityRequest): EntityInfo = GameServer.run {
        it.spawnEntity(request)
    }

    override suspend fun removeEntity(request: RemoveEntityRequest): Empty = GameServer.run {
        it.removeEntity(request)
        EMPTY
    }

    override suspend fun teleport(request: TeleportRequest): EntityInfo = GameServer.run {
        it.teleportEntity(request)
    }

    override suspend fun setVelocity(request: SetVelocityRequest): Empty = GameServer.run {
        it.changeVelocity(request)
        EMPTY
    }

    override suspend fun setRotation(request: SetRotationRequest): Empty = GameServer.run {
        it.changeRotation(request)
        EMPTY
    }

    override suspend fun getNbt(request: EntityRef): Nbt = GameServer.run { it.entityNbt(request) }

    override suspend fun mergeNbt(request: MergeNbtRequest): Empty = GameServer.run {
        it.mergeEntityNbt(request)
        EMPTY
    }

    override suspend fun setCustomName(request: SetCustomNameRequest): Empty = GameServer.run {
        it.changeCustomName(request)
        EMPTY
    }

    override suspend fun setTags(request: SetTagsRequest): Empty = GameServer.run {
        it.changeTags(request)
        EMPTY
    }

    override suspend fun damage(request: DamageRequest): Empty = GameServer.run {
        it.damage(request)
        EMPTY
    }

    override suspend fun setHealth(request: SetHealthRequest): Empty = GameServer.run {
        it.changeHealth(request)
        EMPTY
    }

    override suspend fun getEffects(request: EntityRef): EffectList = GameServer.run {
        it.effectsOf(request)
    }

    override suspend fun addEffect(request: AddEffectRequest): Empty = GameServer.run {
        it.addEffect(request)
        EMPTY
    }

    override suspend fun removeEffect(request: RemoveEffectRequest): Empty = GameServer.run {
        it.removeEffect(request)
        EMPTY
    }

    override suspend fun getAttributes(request: EntityRef): AttributeList = GameServer.run {
        it.attributesOf(request.uuid)
    }

    override suspend fun setAttributeBase(request: SetAttributeBaseRequest): Empty =
        GameServer.run {
            it.changeAttributeBase(request)
            EMPTY
        }

    override suspend fun mount(request: MountRequest): Empty = GameServer.run {
        it.mount(request)
        EMPTY
    }

    override suspend fun dismount(request: EntityRef): Empty = GameServer.run {
        it.dismount(request)
        EMPTY
    }
}
