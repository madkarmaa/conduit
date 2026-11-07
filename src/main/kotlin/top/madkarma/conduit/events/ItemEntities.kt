package top.madkarma.conduit.events

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.toInfo

/** A player picked up or dropped an item entity. */
@Serializable
data class ItemEntityEvent(
    val player: String,
    val playerUuid: String,
    /** For pickups, only the part that fit into the inventory. */
    val item: ItemInfo,
    val itemEntityUuid: String,
    /** Only for drops. Whether the player dropped it by dying. */
    val death: Boolean,
)

/** Publishes ITEM_PICKUP and ITEM_DROP. */
object ItemEntities {
    // Called from the item entity mixin after a player touched it, with the count before.
    @JvmStatic
    fun onTouched(player: Player, itemEntity: ItemEntity, countBefore: Int) {
        if (player !is ServerPlayer || !EventBus.wants()) return

        // A full pickup discards the entity and then restores the stack's count.
        val stack = itemEntity.item
        val pickedUp = if (itemEntity.isRemoved) countBefore else countBefore - stack.count
        if (pickedUp <= 0) return

        EventBus.publish(EventType.ITEM_PICKUP) {
            itemPayload(player, stack.copyWithCount(pickedUp), itemEntity, false)
        }
    }

    // The player drop and death drop mixins call this, only when they created an item entity.
    @JvmStatic
    fun onDropped(player: ServerPlayer, itemEntity: ItemEntity, death: Boolean) {
        EventBus.publish(EventType.ITEM_DROP) {
            itemPayload(player, itemEntity.item, itemEntity, death)
        }
    }

    private fun itemPayload(
        player: ServerPlayer,
        item: ItemStack,
        itemEntity: ItemEntity,
        death: Boolean,
    ) =
        ItemEntityEvent(
            player = player.gameProfile.name,
            playerUuid = player.stringUUID,
            item = item.toInfo(player.registryAccess()),
            itemEntityUuid = itemEntity.stringUUID,
            death = death,
        )
}
