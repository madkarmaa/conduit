package top.madkarma.conduit.service.inventory

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.state.properties.ChestType
import top.madkarma.conduit.game.*

internal fun MinecraftServer.containerContents(
    dimension: String?,
    pos: BlockCoordinates,
): InventoryContents = contents(level(dimension).containerAt(pos.toMc()), 0)

/** A null item empties the slot. */
internal fun MinecraftServer.changeContainerSlot(
    dimension: String?,
    pos: BlockCoordinates,
    slot: Int,
    item: ItemInfo?,
) {
    editContainer(dimension, pos) { container ->
        if (slot !in 0 until container.containerSize) invalidInput("Slot $slot is out of range")
        container.setItem(slot, item?.toMc(registryAccess()) ?: ItemStack.EMPTY)
    }
}

internal fun MinecraftServer.clearContainer(dimension: String?, pos: BlockCoordinates) {
    editContainer(dimension, pos) { it.clearContent() }
}

/** Adds an item stack like a hopper would, and returns how many items did not fit. */
internal fun MinecraftServer.addToContainer(
    dimension: String?,
    pos: BlockCoordinates,
    item: ItemInfo,
): Int {
    var left = 0
    editContainer(dimension, pos) { container ->
        left = HopperBlockEntity.addItem(null, container, item.toMc(registryAccess()), null).count
    }
    return left
}

// Undo saves every block that holds the container's items before the edit.
private fun MinecraftServer.editContainer(
    dimension: String?,
    pos: BlockCoordinates,
    edit: (Container) -> Unit,
) {
    val level = level(dimension)
    val position = pos.toMc()
    val container = level.containerAt(position)

    containerBlocks(level, position).forEach { EditJournal.before(level, it) }
    edit(container)
    container.setChanged()
}

/** The container at a position. A double chest is one container of both halves. */
private fun ServerLevel.containerAt(pos: BlockPos): Container {
    val state = getBlockState(pos)
    val block = state.block
    val container =
        if (block is ChestBlock) ChestBlock.getContainer(block, state, this, pos, true)
        else getBlockEntity(pos) as? Container

    return container ?: notFound("No container at ${pos.x} ${pos.y} ${pos.z}")
}

private fun containerBlocks(level: ServerLevel, pos: BlockPos): List<BlockPos> {
    val state = level.getBlockState(pos)
    if (state.block !is ChestBlock || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
        return listOf(pos)
    }
    return listOf(pos, pos.relative(ChestBlock.getConnectedDirection(state)))
}
