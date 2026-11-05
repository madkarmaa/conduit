package top.madkarma.minecraft.grpc.events

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.service.server.performance

internal fun chatPayload(sender: ServerPlayer, message: String): ChatEvent = chatEvent {
    player = sender.gameProfile.name
    playerUuid = sender.stringUUID
    this.message = message
}

internal fun playerPayload(player: ServerPlayer): PlayerEvent = playerEvent {
    this.player = player.gameProfile.name
    playerUuid = player.stringUUID
    dimension = player.level().dimension().identifier().toString()
    position = player.position().toProto()
}

internal fun dimensionChangePayload(
    player: ServerPlayer,
    origin: ServerLevel,
    destination: ServerLevel,
): DimensionChangeEvent = dimensionChangeEvent {
    this.player = player.gameProfile.name
    playerUuid = player.stringUUID

    from = origin.dimension().identifier().toString()
    to = destination.dimension().identifier().toString()
}

internal fun blockPayload(
    level: Level,
    player: Player?,
    position: BlockPos,
    state: BlockState,
    item: ItemStack?,
): BlockEvent = blockEvent {
    if (player != null) {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID
    }

    dimension = level.dimension().identifier().toString()

    this.pos = position.toProto()
    this.state = state.toProto()

    if (item != null) this.item = item.toProto(level.registryAccess())
}

internal fun damagePayload(
    entity: LivingEntity,
    source: DamageSource,
    amount: Float,
): EntityDamageEvent = entityDamageEvent {
    this.entity = entity.toProto()

    damageType = damageTypeOf(source)
    this.amount = amount

    source.entity?.let { attackerUuid = it.stringUUID }
}

internal fun deathPayload(entity: LivingEntity, source: DamageSource): EntityDeathEvent =
    entityDeathEvent {
        this.entity = entity.toProto()

        damageType = damageTypeOf(source)
        deathMessage = source.getLocalizedDeathMessage(entity).string

        source.entity?.let { attackerUuid = it.stringUUID }
    }

internal fun attackPayload(player: Player, target: Entity): EntityAttackEvent = entityAttackEvent {
    this.player = player.gameProfile.name
    playerUuid = player.stringUUID
    this.target = target.toProto()
}

internal fun tickPayload(server: MinecraftServer): TickEvent = tickEvent {
    mspt = server.performance().mspt
    playerCount = server.playerCount
}

private fun damageTypeOf(source: DamageSource): String =
    source.typeHolder().unwrapKey().map { it.identifier().toString() }.orElse("")
