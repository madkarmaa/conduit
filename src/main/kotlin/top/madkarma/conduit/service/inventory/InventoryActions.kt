package top.madkarma.conduit.service.inventory

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*

// The actions of the inventory tool, which works on a player, a container or an entity.

// A call names a player, a container's position or an entity. This returns the entity.
private fun entityTarget(input: ToolInput): String? {
    val uuid = input.stringOrNull("uuid") ?: return null
    if (input.has("player") || input.has("pos")) invalidInput("Give player, pos or uuid, not more")
    if (input.boolean("ender_chest")) invalidInput("ender_chest needs a player, not uuid")
    return uuid
}

// A call names a player, a container's position or an entity. This returns the position.
private fun containerPos(input: ToolInput): BlockCoordinates? {
    val pos = input.decodeOrNull<BlockCoordinates>("pos") ?: return null
    if (input.has("player")) invalidInput("Give player or pos, not both")
    if (input.boolean("ender_chest")) invalidInput("ender_chest needs a player, not pos")
    return pos
}

internal fun MinecraftServer.readInventory(input: ToolInput): Reply {
    entityTarget(input)?.let {
        return describeEntityItems(entityItems(it))
    }

    val pos = containerPos(input)
    if (pos != null) {
        val contents = containerContents(input.stringOrNull("dimension"), pos)
        return describeInventory(
            "Non-empty slots of the container at ${pos.x} ${pos.y} ${pos.z}:",
            contents,
        )
    }

    val player = input.string("player")
    if (input.boolean("ender_chest")) {
        return describeInventory("Non-empty ender chest slots:", enderChestOf(player))
    }

    val inventory = inventoryOf(player)
    return describeInventory(
        "Selected hotbar slot ${inventory.selectedSlot}; non-empty slots:",
        inventory,
    )
}

// Returns a line with the slot it set and the item now in it.
internal fun MinecraftServer.setSlot(input: ToolInput): String {
    val item = input.decodeOrNull<ItemInfo>("item")
    entityTarget(input)?.let { uuid ->
        input.require("slot_name")
        val name = input.string("slot_name")
        changeEntitySlot(uuid, name, item)
        return slotLine(name, entityItems(uuid).find { it.slot == name }?.item)
    }

    val slot = input.int("slot")
    val dimension = input.stringOrNull("dimension")
    val pos = containerPos(input)
    if (pos != null) {
        changeContainerSlot(dimension, pos, slot, item)
        return slotLine(slot, containerContents(dimension, pos))
    }

    val player = input.string("player")
    val enderChest = input.boolean("ender_chest")
    changeInventorySlot(player, slot, item, enderChest)
    return slotLine(slot, playerItems(player, enderChest))
}

internal fun MinecraftServer.clear(input: ToolInput) {
    entityTarget(input)?.let {
        return clearEntityItems(it)
    }

    val pos = containerPos(input)
    if (pos != null) clearContainer(input.stringOrNull("dimension"), pos)
    else clearInventory(input.string("player"), input.boolean("ender_chest"))
}

internal fun MinecraftServer.give(input: ToolInput): Reply {
    val item = input.decode<ItemInfo>("item")
    val gave = "Gave ${item.count} ${item.id}"

    entityTarget(input)?.let { uuid ->
        val left = giveEntityItem(uuid, item)
        return done(if (left > 0) "$gave, $left did not fit" else gave)
    }

    val pos = containerPos(input)
    if (pos != null) {
        val left = addToContainer(input.stringOrNull("dimension"), pos, item)
        return done(if (left > 0) "$gave, $left did not fit" else gave)
    }

    val player = input.string("player")
    val enderChest = input.boolean("ender_chest")
    val dropped = giveItem(player, item, enderChest)
    val held =
        playerItems(player, enderChest)
            .slots
            .filter { it.item.id == item.id }
            .sumOf { it.item.count }
    val result = if (dropped > 0) "$gave, $dropped dropped" else gave
    return done("$result. The player now holds $held ${item.id}.")
}

private fun MinecraftServer.playerItems(player: String, enderChest: Boolean): InventoryContents =
    if (enderChest) enderChestOf(player) else inventoryOf(player)
