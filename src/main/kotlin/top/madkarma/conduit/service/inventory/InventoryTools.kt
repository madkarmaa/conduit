package top.madkarma.conduit.service.inventory

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*

internal val SLOT = integerSchema(min = 0, max = 40)

val INVENTORY =
    ToolDefinition(
        name = "inventory",
        title = "Inventory",
        description =
            "Works on an online player's inventory, or with pos on a container such as a chest. " +
                "A double chest counts as one container. read lists the items, or the player's " +
                "ender chest. set_slot puts an item in a slot or empties it. clear empties " +
                "every slot. select_slot picks a player's held hotbar slot. give adds an item " +
                "stack. Items that do not fit drop at a player's feet, or stay out of a " +
                "container. Player slots 0-8 are the hotbar, 9-35 the main inventory, 36-39 " +
                "armor from feet to head and 40 the offhand.",
        input =
            input {
                optional("player", PLAYER.described("Player name or UUID. Leave it out for pos."))
                optional("dimension", DIMENSION)
                optional("pos", BLOCK_POS.described("A container's position instead of a player."))
                optional(
                    "ender_chest",
                    booleanSchema("read lists the player's ender chest instead.", default = false),
                )
                optional(
                    "slot",
                    integerSchema("Required for set_slot and select_slot.", min = 0, max = 53),
                )
                optional(
                    "item",
                    ITEM.described(
                        "Required for give. For set_slot, leave it out to empty the slot.",
                    ),
                )
            },
        actions =
            mapOf(
                "read" to readAction { server, input -> server.readInventory(input) },
                "set_slot" to
                    writeAction { server, input ->
                        server.setSlot(input)
                        done("Slot ${input.int("slot")} set")
                    },
                "clear" to
                    destructiveAction { server, input ->
                        val pos = containerPos(input)
                        if (pos != null) server.clearContainer(input.stringOrNull("dimension"), pos)
                        else server.clearInventory(input.string("player"))
                        done("Cleared")
                    },
                "select_slot" to
                    writeAction { server, input ->
                        server.selectHotbarSlot(input.string("player"), input.int("slot"))
                        done("Selected hotbar slot ${input.int("slot")}")
                    },
                "give" to writeAction { server, input -> server.give(input) },
            ),
    )

// A call names either a player or a container's position.
private fun containerPos(input: ToolInput): BlockCoordinates? {
    val pos = input.decodeOrNull<BlockCoordinates>("pos") ?: return null
    if (input.has("player")) invalidInput("Give player or pos, not both")
    return pos
}

private fun MinecraftServer.readInventory(input: ToolInput): Reply {
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

private fun MinecraftServer.setSlot(input: ToolInput) {
    val slot = input.int("slot")
    val item = input.decodeOrNull<ItemInfo>("item")
    val pos = containerPos(input)

    if (pos != null) changeContainerSlot(input.stringOrNull("dimension"), pos, slot, item)
    else changeInventorySlot(input.string("player"), slot, item)
}

private fun MinecraftServer.give(input: ToolInput): Reply {
    val item = input.decode<ItemInfo>("item")
    val pos = containerPos(input)
    val gave = "Gave ${item.count} ${item.id}"

    if (pos != null) {
        val left = addToContainer(input.stringOrNull("dimension"), pos, item)
        return done(if (left > 0) "$gave, $left did not fit" else gave)
    }

    val dropped = giveItem(input.string("player"), item)
    return done(if (dropped > 0) "$gave, $dropped dropped" else gave)
}

internal fun describeInventory(header: String, inventory: InventoryContents): Reply =
    lines(
        header,
        inventory.slots.map { (slot, item) ->
            val components = if (item.components.isEmpty()) "" else " ${item.components}"
            "$slot: ${item.count} ${item.id}$components"
        },
        dataOf("slots" to encoded(inventory.slots)),
    )
