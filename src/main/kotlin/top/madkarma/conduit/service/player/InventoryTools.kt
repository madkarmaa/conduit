package top.madkarma.conduit.service.player

import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.mcp.*

private val SLOT = integerSchema(min = 0, max = 40)

val INVENTORY =
    ToolDefinition(
        name = "inventory",
        title = "Edit inventory",
        description =
            "Edits an online player's inventory. set_slot puts an item in a slot or empties it. " +
                "clear empties the inventory. select_slot picks the held hotbar slot. Slots 0-8 " +
                "are the hotbar, 9-35 the main inventory, 36-39 armor (feet, legs, chest, head) " +
                "and 40 the offhand.",
        input =
            input {
                required("player", PLAYER)
                optional("slot", SLOT.described("Required for set_slot and select_slot."))
                optional("item", ITEM.described("For set_slot. Leave it out to empty the slot."))
            },
        actions =
            mapOf(
                "set_slot" to
                    writeAction { server, input ->
                        input.require("slot")
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
                        input.require("slot")
                        server.selectHotbarSlot(input.string("player"), input.int("slot"))
                        done("Selected hotbar slot ${input.int("slot")}")
                    },
            ),
    )

val GIVE_ITEM =
    ToolDefinition(
        name = "give_item",
        title = "Give item",
        description =
            "Gives an item stack to an online player. Items that do not fit drop at their feet.",
        input =
            input {
                required("player", PLAYER)
                required("item", ITEM)
            },
        actions =
            mapOf(
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

val OFFLINE_PLAYER =
    ToolDefinition(
        name = "offline_player",
        title = "Edit saved player data",
        description =
            "Edits the saved data of an offline player, which applies at their next login. " +
                "merge_nbt merges SNBT into it. set_slot sets an inventory slot like the " +
                "inventory tool. get_player reads saved data.",
        input =
            input {
                required("player", PLAYER)
                optional("snbt", stringSchema("SNBT to merge. Required for merge_nbt."))
                optional("slot", SLOT.described("Required for set_slot."))
                optional("item", ITEM.described("For set_slot. Leave it out to empty the slot."))
            },
        actions =
            mapOf(
                "merge_nbt" to
                    destructiveAction { server, input ->
                        input.require("snbt")
                        server.mergeOfflinePlayerNbt(input.string("player"), input.string("snbt"))
                        done("Saved data merged")
                    },
                "set_slot" to
                    destructiveAction { server, input ->
                        input.require("slot")
                        server.changeOfflineInventorySlot(
                            input.string("player"),
                            input.int("slot"),
                            input.decodeOrNull<ItemInfo>("item"),
                        )
                        done("Slot ${input.int("slot")} set")
                    },
            ),
    )
