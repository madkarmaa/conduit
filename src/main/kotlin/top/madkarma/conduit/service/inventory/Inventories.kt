package top.madkarma.conduit.service.inventory

import kotlinx.serialization.Serializable
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Prediction
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.*

@Serializable data class InventorySlot(val slot: Int, val item: ItemInfo)

/**
 * Player inventory slots: 0-8 hotbar, 9-35 main, 36-39 armor from feet to head, 40 offhand. Lists
 * only the slots that are not empty.
 */
@Serializable
data class InventoryContents(val size: Int, val slots: List<InventorySlot>, val selectedSlot: Int)

internal fun MinecraftServer.inventoryOf(ref: String): InventoryContents {
    val inventory = player(ref).inventory
    return contents(inventory, inventory.selectedSlot)
}

internal fun MinecraftServer.enderChestOf(ref: String): InventoryContents =
    contents(player(ref).enderChestInventory, 0)

/** A null item empties the slot. */
internal fun MinecraftServer.changeInventorySlot(ref: String, slot: Int, item: ItemInfo?) {
    val inventory = player(ref).inventory

    if (slot !in 0 until inventory.containerSize) invalidInput("Slot $slot is out of range")

    inventory.setItem(slot, item?.toMc(registryAccess()) ?: ItemStack.EMPTY)
}

/** Returns how many items did not fit and dropped at the player's feet. */
internal fun MinecraftServer.giveItem(ref: String, item: ItemInfo): Int {
    val player = player(ref)
    val stack = item.toMc(registryAccess())

    player.inventory.add(stack)

    val dropped = stack.count
    if (!stack.isEmpty) player.drop(stack, false, Prediction.SERVER_ONLY)

    return dropped
}

internal fun MinecraftServer.clearInventory(ref: String) {
    player(ref).inventory.clearContent()
}

internal fun MinecraftServer.selectHotbarSlot(ref: String, slot: Int) {
    if (slot !in 0..8) invalidInput("Hotbar slot must be between 0 and 8")
    val player = player(ref)

    player.inventory.selectedSlot = slot
    player.connection.send(ClientboundSetHeldSlotPacket(slot))
}

internal fun MinecraftServer.contents(container: Container, selected: Int): InventoryContents {
    val registries = registryAccess()
    val slots =
        (0 until container.containerSize).mapNotNull { slot ->
            val stack = container.getItem(slot)
            if (stack.isEmpty) null else InventorySlot(slot, stack.toInfo(registries))
        }

    return InventoryContents(size = container.containerSize, slots = slots, selectedSlot = selected)
}
