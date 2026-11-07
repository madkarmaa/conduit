package top.madkarma.conduit.service.player

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.mcp.*

internal val SLOT = integerSchema(min = 0, max = 40)

val INVENTORY =
    ToolDefinition(
        name = "inventory",
        title = "Inventory",
        description =
            "read lists the items of an online player's inventory or ender chest. set_slot puts " +
                "an item in a slot or empties it. clear empties the inventory. select_slot picks " +
                "the held hotbar slot. give adds an item stack, and items that do not fit drop " +
                "at the player's feet. Slots 0-8 are the hotbar, 9-35 the main inventory, 36-39 " +
                "armor (feet, legs, chest, head) and 40 the offhand.",
        input =
            input {
                required("player", PLAYER)
                optional(
                    "ender_chest",
                    booleanSchema("read lists the ender chest instead.", default = false),
                )
                optional("slot", SLOT.described("Required for set_slot and select_slot."))
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
                        server.changeInventorySlot(
                            input.string("player"),
                            input.int("slot"),
                            input.decodeOrNull<ItemInfo>("item"),
                        )
                        done("Slot ${input.int("slot")} set")
                    },
                "clear" to
                    destructiveAction { server, input ->
                        server.clearInventory(input.string("player"))
                        done("Inventory cleared")
                    },
                "select_slot" to
                    writeAction { server, input ->
                        server.selectHotbarSlot(input.string("player"), input.int("slot"))
                        done("Selected hotbar slot ${input.int("slot")}")
                    },
                "give" to
                    writeAction { server, input ->
                        val item = input.decode<ItemInfo>("item")
                        val dropped = server.giveItem(input.string("player"), item)
                        done(
                            "Gave ${item.count} ${item.id}" +
                                if (dropped > 0) ", $dropped dropped" else "",
                        )
                    },
            ),
    )

private fun MinecraftServer.readInventory(input: ToolInput): Reply {
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

internal fun describeInventory(header: String, inventory: InventoryContents): Reply =
    lines(
        header,
        inventory.slots.map { (slot, item) ->
            val components = if (item.components.isEmpty()) "" else " ${item.components}"
            "$slot: ${item.count} ${item.id}$components"
        },
        dataOf("slots" to encoded(inventory.slots)),
    )
