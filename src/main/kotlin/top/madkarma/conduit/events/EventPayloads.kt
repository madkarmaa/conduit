package top.madkarma.conduit.events

import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
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
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.rotationOf
import top.madkarma.conduit.game.toProto
import top.madkarma.conduit.proto.*
import top.madkarma.conduit.service.server.performance

inline fun buildEvent(fill: Event.Builder.() -> Unit): Event {
    val builder =
        Event.newBuilder()
            .setTimestampMs(System.currentTimeMillis())
            .setTick(GameServer.current?.tickCount ?: 0)

    builder.fill()
    return builder.build()
}

internal fun droppedEvent(firstSequence: Long, count: Long): Event = buildEvent {
    eventsDropped = eventsDroppedEvent {
        this.firstSequence = firstSequence
        this.count = count
    }
}

internal fun chatPayload(sender: ServerPlayer, message: String): ChatEvent = chatEvent {
    player = sender.gameProfile.name
    playerUuid = sender.stringUUID
    this.message = message
}

internal fun customClickPayload(player: ServerPlayer, id: Identifier, payload: Tag?) =
    customClickEvent {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID
        this.id = id.toString()
        if (payload != null) this.payload = payload.toString()
    }

internal fun playerPayload(player: ServerPlayer): PlayerEvent = playerEvent {
    this.player = player.gameProfile.name
    playerUuid = player.stringUUID
    dimension = player.level().dimension().identifier().toString()
    position = player.position().toProto()
}

internal fun movePayload(player: ServerPlayer, teleported: Boolean): PlayerMoveEvent =
    playerMoveEvent {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID

        dimension = player.level().dimension().identifier().toString()
        position = player.position().toProto()
        rotation = rotationOf(player.yRot, player.xRot)
        velocity = player.knownMovement.toProto()
        onGround = player.onGround()

        this.teleported = teleported
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
    source.typeHolder().unwrapKey().getOrNull()?.identifier()?.toString() ?: ""
