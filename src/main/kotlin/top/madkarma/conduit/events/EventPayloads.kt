package top.madkarma.conduit.events

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import top.madkarma.conduit.game.*

@Serializable data class ChatEvent(val player: String, val playerUuid: String, val message: String)

@Serializable
data class CustomClickEvent(
    val player: String,
    val playerUuid: String,
    /** The click event's id, such as "example:confirm". */
    val id: String,
    /** The click event's payload as SNBT, such as "\"abc\"" for a string. */
    val payload: String?,
)

@Serializable
data class PlayerEvent(
    val player: String,
    val playerUuid: String,
    val dimension: String,
    val position: Vector3,
)

@Serializable
data class DimensionChangeEvent(
    val player: String,
    val playerUuid: String,
    val from: String,
    val to: String,
)

@Serializable
data class BlockEvent(
    val player: String?,
    val playerUuid: String?,
    val dimension: String,
    val pos: BlockCoordinates,
    val state: BlockStateInfo,
    /** Item in the hand used, for place and use events. */
    val item: ItemInfo?,
)

@Serializable
data class EntityDamageEvent(
    val entity: EntityInfo,
    val damageType: String,
    val amount: Float,
    val attackerUuid: String?,
)

@Serializable
data class EntityDeathEvent(
    val entity: EntityInfo,
    val damageType: String,
    val deathMessage: String,
    val attackerUuid: String?,
)

@Serializable
data class EntityAttackEvent(val player: String, val playerUuid: String, val target: EntityInfo)

@Serializable data class CommandEvent(val source: String, val command: String)

internal fun chatPayload(sender: ServerPlayer, message: String) =
    ChatEvent(sender.gameProfile.name, sender.stringUUID, message)

internal fun customClickPayload(player: ServerPlayer, id: Identifier, payload: Tag?) =
    CustomClickEvent(player.gameProfile.name, player.stringUUID, id.toString(), payload?.toString())

internal fun playerPayload(player: ServerPlayer) =
    PlayerEvent(
        player = player.gameProfile.name,
        playerUuid = player.stringUUID,
        dimension = player.level().dimension().identifier().toString(),
        position = player.position().toVector(),
    )

internal fun dimensionChangePayload(
    player: ServerPlayer,
    origin: ServerLevel,
    destination: ServerLevel,
) =
    DimensionChangeEvent(
        player = player.gameProfile.name,
        playerUuid = player.stringUUID,
        from = origin.dimension().identifier().toString(),
        to = destination.dimension().identifier().toString(),
    )

internal fun blockPayload(
    level: Level,
    player: Player?,
    position: BlockPos,
    state: BlockState,
    item: ItemStack?,
) =
    BlockEvent(
        player = player?.gameProfile?.name,
        playerUuid = player?.stringUUID,
        dimension = level.dimension().identifier().toString(),
        pos = position.toCoordinates(),
        state = state.toInfo(),
        item = item?.toInfo(level.registryAccess()),
    )

internal fun damagePayload(entity: LivingEntity, source: DamageSource, amount: Float) =
    EntityDamageEvent(entity.toInfo(), damageTypeOf(source), amount, source.entity?.stringUUID)

internal fun deathPayload(entity: LivingEntity, source: DamageSource) =
    EntityDeathEvent(
        entity = entity.toInfo(),
        damageType = damageTypeOf(source),
        deathMessage = source.getLocalizedDeathMessage(entity).string,
        attackerUuid = source.entity?.stringUUID,
    )

internal fun attackPayload(player: Player, target: Entity) =
    EntityAttackEvent(player.gameProfile.name, player.stringUUID, target.toInfo())

private fun damageTypeOf(source: DamageSource): String =
    source.typeHolder().unwrapKey().getOrNull()?.identifier()?.toString() ?: ""
