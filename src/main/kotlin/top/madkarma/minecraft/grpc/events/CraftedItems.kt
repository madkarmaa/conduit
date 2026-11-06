package top.madkarma.minecraft.grpc.events

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.RecipeCraftingHolder
import net.minecraft.world.item.ItemStack
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.EventType
import top.madkarma.minecraft.grpc.proto.ItemCraftEvent
import top.madkarma.minecraft.grpc.proto.itemCraftEvent

/**
 * Publishes EVENT_TYPE_ITEM_CRAFT and EVENT_TYPE_ITEM_SMELT. Shift-clicking a result slot takes
 * one batch after another within a single click, so batches are added up until the click ends.
 */
object CraftedItems {
    private class Take(
        val type: EventType,
        val player: ServerPlayer,
        val result: ItemStack,
        val recipeId: String?,
    ) {
        fun continues(other: Take): Boolean =
            type == other.type && player == other.player && recipeId == other.recipeId && ItemStack.isSameItemSameComponents(
                result, other.result
            )
    }

    // Only touched on the server thread.
    private var clickInProgress = false
    private var pendingTake: Take? = null

    // Called from the container menu mixin before and after every click.
    @JvmStatic
    fun onClickStarted(player: Player) {
        if (player is ServerPlayer) clickInProgress = true
    }

    @JvmStatic
    fun onClickFinished(player: Player) {
        if (player !is ServerPlayer) return

        clickInProgress = false
        publishPendingTake()
    }

    // Called from the crafting result slot mixin.
    @JvmStatic
    fun onCrafted(player: Player, result: ItemStack, count: Int, resultContainer: Container) {
        if (player !is ServerPlayer || !EventBus.wants(EventType.EVENT_TYPE_ITEM_CRAFT)) return

        val recipe = (resultContainer as? RecipeCraftingHolder)?.recipeUsed
        val recipeId = recipe?.id()?.identifier()?.toString()
        add(Take(EventType.EVENT_TYPE_ITEM_CRAFT, player, result.copyWithCount(count), recipeId))
    }

    // Called from the furnace result slot mixin.
    @JvmStatic
    fun onSmelted(player: Player, result: ItemStack, count: Int) {
        if (player !is ServerPlayer || !EventBus.wants(EventType.EVENT_TYPE_ITEM_SMELT)) return

        add(Take(EventType.EVENT_TYPE_ITEM_SMELT, player, result.copyWithCount(count), null))
    }

    private fun add(take: Take) {
        val pending = pendingTake
        if (pending != null && pending.continues(take)) {
            pending.result.grow(take.result.count)
            return
        }

        publishPendingTake()
        pendingTake = take
        if (!clickInProgress) publishPendingTake()
    }

    private fun publishPendingTake() {
        val take = pendingTake ?: return
        pendingTake = null

        EventBus.publish(take.type, accepts = { it.wantsPlayer(take.player) }) {
            if (take.type == EventType.EVENT_TYPE_ITEM_CRAFT) {
                itemCraft = craftPayload(take)
            } else {
                itemSmelt = craftPayload(take)
            }
        }
    }

    private fun craftPayload(take: Take): ItemCraftEvent = itemCraftEvent {
        player = take.player.gameProfile.name
        playerUuid = take.player.stringUUID
        result = take.result.toProto(take.player.registryAccess())
        take.recipeId?.let { recipeId = it }
        count = take.result.count
    }
}
