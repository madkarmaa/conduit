package top.madkarma.conduit.events

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.toProto
import top.madkarma.conduit.proto.EventType
import top.madkarma.conduit.proto.ItemEntityEvent
import top.madkarma.conduit.proto.copy
import top.madkarma.conduit.proto.itemEntityEvent

/** Publishes EVENT_TYPE_ITEM_PICKUP and EVENT_TYPE_ITEM_DROP. */
object ItemEntities {
    // Called from the item entity mixin after a player touched it, with the count before.
    @JvmStatic
    fun onTouched(player: Player, itemEntity: ItemEntity, countBefore: Int) {
        if (player !is ServerPlayer || !EventBus.wants(EventType.EVENT_TYPE_ITEM_PICKUP)) return

        // A full pickup discards the entity and then restores the stack's count.
        val stack = itemEntity.item
        val pickedUp = if (itemEntity.isRemoved) countBefore else countBefore - stack.count
        if (pickedUp <= 0) return

        EventBus.publish(EventType.EVENT_TYPE_ITEM_PICKUP, accepts = { it.wantsPlayer(player) }) {
            itemPickup = itemPayload(player, stack.copyWithCount(pickedUp), itemEntity)
        }
    }

    // The player drop and death drop mixins call this, only when they created an item entity.
    @JvmStatic
    fun onDropped(player: ServerPlayer, itemEntity: ItemEntity, death: Boolean) {
        EventBus.publish(EventType.EVENT_TYPE_ITEM_DROP, accepts = { it.wantsPlayer(player) }) {
            itemDrop = itemPayload(player, itemEntity.item, itemEntity).copy { this.death = death }
        }
    }

    private fun itemPayload(
        player: ServerPlayer,
        item: ItemStack,
        itemEntity: ItemEntity,
    ): ItemEntityEvent = itemEntityEvent {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID

        this.item = item.toProto(player.registryAccess())
        itemEntityUuid = itemEntity.stringUUID
    }
}
