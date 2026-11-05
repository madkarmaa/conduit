package top.madkarma.minecraft.grpc.service.entity

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import top.madkarma.minecraft.grpc.game.entity
import top.madkarma.minecraft.grpc.game.parseId
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument

private const val DEFAULT_EFFECT_TICKS = 600

internal fun MinecraftServer.living(uuid: String): LivingEntity =
    entity(uuid) as? LivingEntity ?: failedPrecondition("Entity $uuid is not a living entity")

internal fun MinecraftServer.changeHealth(request: SetHealthRequest) {
    living(request.uuid).health = request.health
}

internal fun MinecraftServer.damage(request: DamageRequest) {
    val entity = entity(request.uuid)
    val typeId = request.damageType.ifBlank { "minecraft:generic" }
    val type = registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
        .get(ResourceKey.create(Registries.DAMAGE_TYPE, parseId(typeId)))
        .orElseGet { invalidArgument("Unknown damage type: $typeId") }
    val attacker = if (request.hasAttacker()) entity(request.attacker) else null

    entity.hurtServer(entity.level() as ServerLevel, DamageSource(type, attacker), request.amount)
}

internal fun MinecraftServer.effectsOf(request: EntityRef): EffectList = effectList {
    effects += living(request.uuid).activeEffects.map { it.toProto() }
}

internal fun MinecraftServer.addEffect(request: AddEffectRequest) {
    val effect = request.effect
    val holder = BuiltInRegistries.MOB_EFFECT.get(parseId(effect.id)).orElseGet {
        invalidArgument("Unknown effect: ${effect.id}")
    }
    val duration = if (effect.duration == 0) DEFAULT_EFFECT_TICKS else effect.duration
    val instance = MobEffectInstance(
        holder,
        duration,
        effect.amplifier,
        effect.ambient,
        effect.visible,
        effect.showIcon,
    )

    living(request.uuid).addEffect(instance)
}

internal fun MinecraftServer.removeEffect(request: RemoveEffectRequest) {
    val entity = living(request.uuid)

    if (request.effect.isBlank()) {
        entity.removeAllEffects()
        return
    }

    val holder = BuiltInRegistries.MOB_EFFECT.get(parseId(request.effect))
        .orElseGet { invalidArgument("Unknown effect: ${request.effect}") }

    entity.removeEffect(holder)
}

internal fun MinecraftServer.attributesOf(uuid: String): AttributeList {
    val entity = living(uuid)

    return attributeList {
        BuiltInRegistries.ATTRIBUTE.listElements().forEach { holder ->
            val instance = entity.getAttribute(holder) ?: return@forEach

            attributes += attribute {
                id = holder.key().identifier().toString()
                base = instance.baseValue
                value = instance.value
            }
        }
    }
}

internal fun MinecraftServer.changeAttributeBase(request: SetAttributeBaseRequest) {
    val holder = BuiltInRegistries.ATTRIBUTE.get(parseId(request.attribute))
        .orElseGet { invalidArgument("Unknown attribute: ${request.attribute}") }

    val instance = living(request.uuid).getAttribute(holder)
        ?: failedPrecondition("Entity has no attribute ${request.attribute}")

    instance.baseValue = request.base
}
