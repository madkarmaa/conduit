package top.madkarma.conduit.service.inventory

import top.madkarma.conduit.mcp.*

internal val SLOT = integerSchema(min = 0, max = 40)

val INVENTORY =
    ToolDefinition(
        name = "inventory",
        title = "Inventory",
        description =
            "Works on an online player's inventory, with pos on a container such as a chest, " +
                "or with uuid on an entity's items. A double chest counts as one container, " +
                "and a lectern's book and a campfire's food count as containers of one item " +
                "per slot. " +
                "Entities name their slots like the /item command, such as \"container.3\", " +
                "\"armor.head\", \"weapon.mainhand\", \"saddle\" or \"mob.inventory.0\", and read " +
                "lists the slots that hold items. give " +
                "fills an entity's storage, such as a chest minecart's, a donkey's chest or a " +
                "villager's inventory. With ender_chest, the actions but " +
                "select_slot work on the player's ender chest, slots 0-26, instead. read lists " +
                "the items. set_slot puts an item in a slot or empties it. clear empties " +
                "every slot. select_slot picks a player's held hotbar slot. give adds an item " +
                "stack. Items that do not fit drop at a player's feet, or stay out of a " +
                "container. Player slots 0-8 are the hotbar, 9-35 the main inventory, 36-39 " +
                "armor from feet to head and 40 the offhand.",
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
                "set_slot" to
                    writeAction { server, input ->
                        done("Slot ${server.setSlot(input)} set")
                    },
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
