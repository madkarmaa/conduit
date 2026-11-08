package top.madkarma.conduit.service.inventory

import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.ItemTags
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LecternBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.CampfireBlockEntity
import net.minecraft.world.level.block.entity.LecternBlockEntity
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mixin.CampfireBlockEntityAccessor

/**
 * Lecterns and campfires hold items without being containers in the game. These adapters let the
 * container code read and change them. Each slot holds one item.
 */
internal fun itemHolder(level: ServerLevel, blockEntity: BlockEntity?): Container? =
    when (blockEntity) {
        is LecternBlockEntity -> LecternItems(level, blockEntity)
        is CampfireBlockEntity -> CampfireItems(level, blockEntity)
        else -> null
    }

private abstract class OneItemSlots(
    private val level: ServerLevel,
    private val block: BlockEntity,
) : Container {
    override fun getMaxStackSize(): Int = 1

    override fun isEmpty(): Boolean = (0 until containerSize).all { getItem(it).isEmpty }

    override fun removeItem(slot: Int, count: Int): ItemStack =
        getItem(slot).copy().also { setItem(slot, ItemStack.EMPTY) }

    override fun removeItemNoUpdate(slot: Int): ItemStack = removeItem(slot, 1)

    override fun stillValid(player: Player): Boolean = true

    override fun clearContent() {
        for (slot in 0 until containerSize) setItem(slot, ItemStack.EMPTY)
    }

    override fun setChanged() {
        block.setChanged()
        level.sendBlockUpdated(block.blockPos, block.blockState, block.blockState, Block.UPDATE_ALL)
    }
}

private class LecternItems(
    private val level: ServerLevel,
    private val lectern: LecternBlockEntity,
) : OneItemSlots(level, lectern) {
    override fun getContainerSize(): Int = 1

    override fun getItem(slot: Int): ItemStack = lectern.book

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        stack.`is`(ItemTags.LECTERN_BOOKS) && lectern.book.isEmpty

    override fun setItem(slot: Int, stack: ItemStack) {
        if (!stack.isEmpty && !stack.`is`(ItemTags.LECTERN_BOOKS)) {
            invalidInput("A lectern only holds a book and quill or a written book")
        }

        lectern.book = stack.copyWithCount(stack.count.coerceAtMost(1))
        val state = level.getBlockState(lectern.blockPos)
        LecternBlock.resetBookState(null, level, lectern.blockPos, state, !stack.isEmpty)
    }
}

private class CampfireItems(
    private val level: ServerLevel,
    private val campfire: CampfireBlockEntity,
) : OneItemSlots(level, campfire) {
    private val timers = campfire as CampfireBlockEntityAccessor

    override fun getContainerSize(): Int = campfire.items.size

    override fun getItem(slot: Int): ItemStack = campfire.items[slot]

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        campfire.items[slot].isEmpty && cookingTime(stack) != null

    override fun setItem(slot: Int, stack: ItemStack) {
        val time =
            if (stack.isEmpty) 0
            else cookingTime(stack) ?: invalidInput("A campfire cannot cook ${stack.itemName}")

        campfire.items[slot] = stack.copyWithCount(stack.count.coerceAtMost(1))
        timers.cookingTime[slot] = time
        timers.cookingProgress[slot] = 0
    }

    private fun cookingTime(stack: ItemStack): Int? =
        level
            .recipeAccess()
            .getRecipeFor(RecipeType.CAMPFIRE_COOKING, SingleRecipeInput(stack), level)
            .map { it.value().cookingTime() }
            .orElse(null)
}
