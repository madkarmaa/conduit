package top.madkarma.conduit.service.inventory

import top.madkarma.conduit.mcp.*

internal val SLOT = integerSchema(min = 0, max = 40)

val INVENTORY =
    ToolDefinition(
        name = "inventory",
        title = "Inventory",
        description =
            "Works on a player's inventory, a container at pos, or an entity's items by " +
                "uuid. read lists the items. set_slot sets or empties a slot and returns what " +
                "it holds. clear empties every slot. select_slot picks the held hotbar slot. " +
                "give adds a stack, drops what does not fit at a player's feet, and returns " +
                "the player's new total. ender_chest uses the ender chest, slots 0-26. Player " +
                "slots are 0-8 hotbar, 9-35 main, 36-39 armor from feet to head and 40 " +
                "offhand. Entity slots use /item names, such as \"armor.head\" or " +
                "\"container.3\". A double chest is one container.",
        input =
            input {
                optional("player", PLAYER.described("Player name or UUID. Leave it out for pos."))
                optional("dimension", DIMENSION)
                optional("pos", BLOCK_POS.described("A container's position instead of a player."))
                optional("uuid", UUID.described("An entity, not a player, instead of a player."))
                optional(
                    "ender_chest",
                    booleanSchema("Use the player's ender chest instead.", default = false),
                )
                optional(
                    "slot",
                    integerSchema("Required for set_slot and select_slot.", min = 0, max = 53),
                )
                optional(
                    "slot_name",
                    stringSchema("An entity's slot, such as \"armor.head\". For set_slot."),
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
                "set_slot" to writeAction { server, input -> done(server.setSlot(input)) },
                "clear" to
                    destructiveAction { server, input ->
                        server.clear(input)
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
