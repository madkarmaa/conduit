package top.madkarma.conduit.service.entity

import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import top.madkarma.conduit.game.*

private const val DEFAULT_EFFECT_TICKS = 600

@Serializable data class EffectList(val effects: List<EffectInfo>)

@Serializable data class AttributeInfo(val id: String, val base: Double, val value: Double)

@Serializable data class AttributeList(val attributes: List<AttributeInfo>)

internal fun MinecraftServer.living(uuid: String): LivingEntity =
    entity(uuid) as? LivingEntity ?: failedPrecondition("Entity $uuid is not a living entity")

internal fun MinecraftServer.changeHealth(uuid: String, health: Float) {
    editedLiving(uuid).health = health
}

/** Hurts the entity like an attack. The attacker is an optional entity UUID. */
internal fun MinecraftServer.damage(
    uuid: String,
    amount: Float,
    damageType: String?,
    attacker: String?,
) {
    val entity = editedEntity(uuid)
    val typeId = damageType.orEmpty().ifBlank { "minecraft:generic" }
    val type =
        registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .get(ResourceKey.create(Registries.DAMAGE_TYPE, parseId(typeId)))
            .orElseGet { invalidInput("Unknown damage type: $typeId") }
    val source = DamageSource(type, attacker?.let { entity(it) })

    entity.hurtServer(entity.level() as ServerLevel, source, amount)
}

internal fun MinecraftServer.effectsOf(uuid: String): EffectList =
    EffectList(living(uuid).activeEffects.map { it.toInfo() })

/** A duration of 0 ticks means 30 seconds, and -1 means infinite. */
internal fun MinecraftServer.addEffect(uuid: String, effect: EffectInfo) {
    val holder =
        BuiltInRegistries.MOB_EFFECT.get(parseId(effect.id)).orElseGet {
            invalidInput("Unknown effect: ${effect.id}")
        }
    val duration = if (effect.duration == 0) DEFAULT_EFFECT_TICKS else effect.duration
    val instance =
        MobEffectInstance(
            holder,
            duration,
            effect.amplifier,
            effect.ambient,
            effect.visible,
            effect.showIcon,
        )

    editedLiving(uuid).addEffect(instance)
}

/** A null effect clears all effects. */
internal fun MinecraftServer.removeEffect(uuid: String, effect: String?) {
    val entity = editedLiving(uuid)

    if (effect.isNullOrBlank()) {
        entity.removeAllEffects()
        return
    }

    val holder =
        BuiltInRegistries.MOB_EFFECT.get(parseId(effect)).orElseGet {
            invalidInput("Unknown effect: $effect")
        }

    entity.removeEffect(holder)
}

internal fun MinecraftServer.attributesOf(uuid: String): AttributeList {
    val entity = living(uuid)

    val attributes =
        BuiltInRegistries.ATTRIBUTE.listElements().toList().mapNotNull { holder ->
            val instance = entity.getAttribute(holder) ?: return@mapNotNull null
            AttributeInfo(holder.key().identifier().toString(), instance.baseValue, instance.value)
        }

    return AttributeList(attributes)
}

internal fun MinecraftServer.changeAttributeBase(uuid: String, attribute: String, base: Double) {
    val holder =
        BuiltInRegistries.ATTRIBUTE.get(parseId(attribute)).orElseGet {
            invalidInput("Unknown attribute: $attribute")
        }

    val instance =
        editedLiving(uuid).getAttribute(holder)
            ?: failedPrecondition("Entity has no attribute $attribute")

    instance.baseValue = base
}
