package top.madkarma.minecraft.grpc.service.entity

import com.google.protobuf.Empty
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.game.entity
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.AddEffectRequest
import top.madkarma.minecraft.grpc.proto.AttributeList
import top.madkarma.minecraft.grpc.proto.DamageRequest
import top.madkarma.minecraft.grpc.proto.EffectList
import top.madkarma.minecraft.grpc.proto.EntityInfo
import top.madkarma.minecraft.grpc.proto.EntityList
import top.madkarma.minecraft.grpc.proto.EntityRef
import top.madkarma.minecraft.grpc.proto.EntityServiceGrpcKt
import top.madkarma.minecraft.grpc.proto.ListEntitiesRequest
import top.madkarma.minecraft.grpc.proto.MergeNbtRequest
import top.madkarma.minecraft.grpc.proto.MountRequest
import top.madkarma.minecraft.grpc.proto.Nbt
import top.madkarma.minecraft.grpc.proto.RemoveEffectRequest
import top.madkarma.minecraft.grpc.proto.RemoveEntityRequest
import top.madkarma.minecraft.grpc.proto.SetAttributeBaseRequest
import top.madkarma.minecraft.grpc.proto.SetCustomNameRequest
import top.madkarma.minecraft.grpc.proto.SetHealthRequest
import top.madkarma.minecraft.grpc.proto.SetRotationRequest
import top.madkarma.minecraft.grpc.proto.SetTagsRequest
import top.madkarma.minecraft.grpc.proto.SetVelocityRequest
import top.madkarma.minecraft.grpc.proto.SpawnEntityRequest
import top.madkarma.minecraft.grpc.proto.TeleportRequest
import top.madkarma.minecraft.grpc.proto.effectList
import top.madkarma.minecraft.grpc.proto.entityList
import top.madkarma.minecraft.grpc.proto.nbt

private val EMPTY: Empty = Empty.getDefaultInstance()

class EntityServiceImpl : EntityServiceGrpcKt.EntityServiceCoroutineImplBase() {
    override suspend fun listEntities(request: ListEntitiesRequest): EntityList =
        GameServer.run { server ->
            entityList {
                entities +=
                    server.findEntities(request).map { it.toProto() }
            }
        }

    override suspend fun getEntity(request: EntityRef): EntityInfo = GameServer.run {
        it.entity(request.uuid).toProto()
    }

    override suspend fun spawnEntity(request: SpawnEntityRequest): EntityInfo = GameServer.run {
        it.spawnEntity(request).toProto()
    }

    override suspend fun removeEntity(request: RemoveEntityRequest): Empty = GameServer.run {
        it.removeEntity(request)
        EMPTY
    }

    override suspend fun teleport(request: TeleportRequest): EntityInfo = GameServer.run {
        it.teleportEntity(request).toProto()
    }

    override suspend fun setVelocity(request: SetVelocityRequest): Empty = GameServer.run {
        it.changeVelocity(request)
        EMPTY
    }

    override suspend fun setRotation(request: SetRotationRequest): Empty = GameServer.run {
        rotate(it.entity(request.uuid), request.rotation.yaw, request.rotation.pitch)
        EMPTY
    }

    override suspend fun getNbt(request: EntityRef): Nbt = GameServer.run { server ->
        nbt { snbt = server.saveEntity(server.entity(request.uuid)).toString() }
    }

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
        it.living(request.uuid).health = request.health
        EMPTY
    }

    override suspend fun getEffects(request: EntityRef): EffectList = GameServer.run { server ->
        effectList {
            effects +=
                server.living(request.uuid).activeEffects.map { it.toProto() }
        }
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
        it.entity(request.uuid).stopRiding()
        EMPTY
    }
}
