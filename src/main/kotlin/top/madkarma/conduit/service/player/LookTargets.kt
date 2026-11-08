package top.madkarma.conduit.service.player

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.BlockStateInfo
import top.madkarma.conduit.game.toCoordinates
import top.madkarma.conduit.game.toInfo

@Serializable
data class LookedAtBlock(
    val pos: BlockCoordinates,
    val state: BlockStateInfo,
    /** The face the player looks at, such as "north" or "up". */
    val face: String,
)

@Serializable data class LookedAtEntity(val uuid: String, val type: String, val name: String)

/** What a player looks at within their reach: a block or an entity, whichever is nearer. */
@Serializable
data class LookTarget(
    /** Blocks from the player's eyes. */
    val distance: Double,
    val block: LookedAtBlock? = null,
    val entity: LookedAtEntity? = null,
)

internal fun lookTarget(player: ServerPlayer): LookTarget? {
    val block =
        (player.pick(player.blockInteractionRange(), 1f, false) as? BlockHitResult)?.takeIf {
            it.type == HitResult.Type.BLOCK
        }
    val entity = lookedAtEntity(player)

    if (entity != null && (block == null || distance(player, entity) < distance(player, block))) {
        return LookTarget(distance(player, entity), entity = describe(entity.entity))
    }

    if (block == null) return null
    return LookTarget(distance(player, block), block = describe(player, block))
}

private fun lookedAtEntity(player: ServerPlayer): EntityHitResult? {
    val reach = player.entityInteractionRange()
    val eyes = player.eyePosition
    val view = player.getViewVector(1f).scale(reach)
    val area = player.boundingBox.expandTowards(view).inflate(1.0)

    return ProjectileUtil.getEntityHitResult(
        player,
        eyes,
        eyes.add(view),
        area,
        { !it.isSpectator && it.isPickable },
        reach * reach,
    )
}

private fun describe(player: ServerPlayer, hit: BlockHitResult): LookedAtBlock =
    LookedAtBlock(
        pos = hit.blockPos.toCoordinates(),
        state = player.level().getBlockState(hit.blockPos).toInfo(),
        face = hit.direction.serializedName,
    )

private fun describe(entity: Entity): LookedAtEntity =
    LookedAtEntity(
        uuid = entity.stringUUID,
        type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
        name = entity.name.string,
    )

private fun distance(player: ServerPlayer, hit: HitResult): Double =
    (player.eyePosition.distanceTo(hit.location) * 100).roundToLong() / 100.0
