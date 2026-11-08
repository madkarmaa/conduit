package top.madkarma.conduit.service.player

import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.ItemStackWithSlot
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.EntityEquipment
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.PlayerEnderChestContainer
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.level.storage.ValueInput
import top.madkarma.conduit.game.invalidInput

// The inventory and ender chest in a player's saved data.

internal fun readEnderChest(input: ValueInput): PlayerEnderChestContainer =
    PlayerEnderChestContainer().apply {
        fromSlots(input.listOrEmpty("EnderItems", ItemStackWithSlot.CODEC))
    }

internal fun writeEnderChest(
    data: CompoundTag,
    container: PlayerEnderChestContainer,
    registries: HolderLookup.Provider,
) {
    val problems = ProblemReporter.Collector()
    val output = TagValueOutput.createWithContext(problems, registries)
    container.storeAsSlots(output.list("EnderItems", ItemStackWithSlot.CODEC))
    if (!problems.isEmpty) invalidInput("Item cannot be saved: ${problems.report}")

    data.remove("EnderItems")
    data.merge(output.buildResult())
}

/** Slot numbers follow the online inventory, with the main items first and then the equipment. */
internal fun readInventory(input: ValueInput): SimpleContainer {
    val container = SimpleContainer(INVENTORY_SLOTS)

    for (item in input.listOrEmpty("Inventory", ItemStackWithSlot.CODEC)) {
        if (!item.isValidInContainer(Inventory.INVENTORY_SIZE)) continue
        container.setItem(item.slot, item.stack)
    }

    val equipment = input.read("equipment", EntityEquipment.CODEC).getOrNull() ?: EntityEquipment()
    for ((slot, equipmentSlot) in Inventory.EQUIPMENT_SLOT_MAPPING) {
        container.setItem(slot, equipment.get(equipmentSlot))
    }

    return container
}

internal fun writeInventory(
    data: CompoundTag,
    container: SimpleContainer,
    registries: HolderLookup.Provider,
) {
    val problems = ProblemReporter.Collector()
    val output = TagValueOutput.createWithContext(problems, registries)
    val items = output.list("Inventory", ItemStackWithSlot.CODEC)

    for (slot in 0 until Inventory.INVENTORY_SIZE) {
        val stack = container.getItem(slot)
        if (!stack.isEmpty) items.add(ItemStackWithSlot(slot, stack))
    }

    val equipment = EntityEquipment()
    for ((slot, equipmentSlot) in Inventory.EQUIPMENT_SLOT_MAPPING) {
        equipment.set(equipmentSlot, container.getItem(slot))
    }
    if (!equipment.isEmpty) output.store("equipment", EntityEquipment.CODEC, equipment)

    // Without this check, the game would leave an item it cannot save out of the file without
    // an error.
    if (!problems.isEmpty) invalidInput("Item cannot be saved: ${problems.report}")

    data.remove("equipment")
    data.merge(output.buildResult())
}
