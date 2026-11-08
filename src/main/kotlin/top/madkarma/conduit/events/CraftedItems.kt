package top.madkarma.conduit.events

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.RecipeCraftingHolder
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.toInfo

/**
 * A player took the result out of a crafting grid or a furnace, blast furnace or smoker. Several
 * batches taken with one shift-click are one event with the total.
 */
@Serializable
data class ItemCraftEvent(
    val player: String,
    val playerUuid: String,
    /** The item taken, with the total count. */
    val result: ItemInfo,
    /** The recipe used, such as "minecraft:crafting_table". Not set for smelting. */
    val recipeId: String?,
    /** Number of items taken. */
    val count: Int,
)

/**
 * Publishes ITEM_CRAFT and ITEM_SMELT. Shift-clicking a result slot takes one batch after another
 * within a single click, so this adds up the batches until the click ends.
 */
object CraftedItems {
    private class Take(
        val type: EventType,
        val player: ServerPlayer,
        val result: ItemStack,
        val recipeId: String?,
    ) {
        fun continues(other: Take): Boolean =
            type == other.type &&
                player == other.player &&
                recipeId == other.recipeId &&
                ItemStack.isSameItemSameComponents(
                    result,
                    other.result,
                )
    }

    // Only the server thread touches this.
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
        if (player !is ServerPlayer || !EventBus.wants()) return

        val recipe = (resultContainer as? RecipeCraftingHolder)?.recipeUsed
        val recipeId = recipe?.id()?.identifier()?.toString()
        add(Take(EventType.ITEM_CRAFT, player, result.copyWithCount(count), recipeId))
    }

    // Called from the furnace result slot mixin.
    @JvmStatic
    fun onSmelted(player: Player, result: ItemStack, count: Int) {
        if (player !is ServerPlayer || !EventBus.wants()) return
        add(Take(EventType.ITEM_SMELT, player, result.copyWithCount(count), null))
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

        EventBus.publish(take.type) { craftPayload(take) }
    }

    private fun craftPayload(take: Take) =
        ItemCraftEvent(
            player = take.player.gameProfile.name,
            playerUuid = take.player.stringUUID,
            result = take.result.toInfo(take.player.registryAccess()),
            recipeId = take.recipeId,
            count = take.result.count,
        )
}
