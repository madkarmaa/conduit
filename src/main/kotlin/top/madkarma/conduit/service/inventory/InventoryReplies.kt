package top.madkarma.conduit.service.inventory

import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.mcp.*

// How the inventory tool shows slots and items.

internal fun describeInventory(header: String, inventory: InventoryContents): Reply =
    lines(
        header,
        inventory.slots.map { (slot, item) -> "$slot: ${itemText(item)}" },
        dataOf("slots" to encoded(inventory.slots)),
    )

internal fun describeEntityItems(slots: List<EntitySlot>): Reply =
    lines(
        "Entity slots that hold items:",
        slots.map { (slot, item) -> "$slot: ${itemText(item)}" },
        dataOf("slots" to encoded(slots)),
    )

internal fun slotLine(slot: Int, contents: InventoryContents): String =
    slotLine(slot.toString(), contents.slots.find { it.slot == slot }?.item)

internal fun slotLine(slot: String, item: ItemInfo?): String =
    "Slot $slot: ${item?.let(::itemText) ?: "empty"}"

private fun itemText(item: ItemInfo): String {
    val components = if (item.components.isEmpty()) "" else " ${item.components}"
    return "${item.count} ${item.id}$components"
}
