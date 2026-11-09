package top.madkarma.conduit.service.player

import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.inventory.SLOT

val EDIT_PLAYER =
    ToolDefinition(
        name = "edit_player",
        title = "Edit player",
        description =
            "game_mode, food, experience and operator change an online player. " +
                "grant_advancement and revoke_advancement change an advancement or one of its " +
                "criteria. saved_merge_nbt and saved_set_slot edit an offline player's saved " +
                "data, which applies at their next login. With ender_chest, saved_set_slot " +
                "uses ender chest slots 0-26.",
        input =
            input {
                required("player", PLAYER)
                optional(
                    "game_mode",
                    enumSchema(listOf("survival", "creative", "adventure", "spectator")),
                )
                optional("food_level", integerSchema(min = 0, max = 20))
                optional("saturation", numberSchema(min = 0.0))
                optional("levels", integerSchema(min = 0))
                optional("points", integerSchema(min = 0))
                optional("operator", booleanSchema("Required for operator."))
                optional("snbt", stringSchema("SNBT to merge. Required for saved_merge_nbt."))
                optional(
                    "advancement",
                    stringSchema(
                        "Advancement id, such as \"minecraft:story/mine_diamond\".",
                    ),
                )
                optional("criterion", stringSchema("One criterion. Leave it out for all of them."))
                optional("slot", SLOT.described("Required for saved_set_slot."))
                optional("ender_chest", booleanSchema("For saved_set_slot.", default = false))
                optional(
                    "item",
                    ITEM.described("For saved_set_slot. Leave it out to empty the slot."),
                )
            },
        actions =
            mapOf(
                "game_mode" to
                    writeAction { server, input ->
                        input.require("game_mode")
                        server.changeGameMode(input.string("player"), input.string("game_mode"))
                        done("Game mode set to ${input.string("game_mode")}")
                    },
                "food" to
                    writeAction { server, input ->
                        server.changeFood(
                            input.string("player"),
                            input.intOrNull("food_level"),
                            input.floatOrNull("saturation"),
                        )
                        done("Food updated")
                    },
                "experience" to
                    writeAction { server, input ->
                        server.changeExperience(
                            input.string("player"),
                            input.intOrNull("levels"),
                            input.intOrNull("points"),
                        )
                        done("Experience updated")
                    },
                "operator" to
                    destructiveAction { server, input ->
                        input.require("operator")
                        val operator = input.boolean("operator")
                        server.changeOperator(input.string("player"), operator)
                        done(if (operator) "Player is now operator" else "Operator removed")
                    },
                "grant_advancement" to
                    writeAction { server, input ->
                        val granted =
                            server.grantAdvancement(
                                input.string("player"),
                                input.string("advancement"),
                                input.stringOrNull("criterion"),
                            )
                        done("Granted $granted criteria")
                    },
                "revoke_advancement" to
                    writeAction { server, input ->
                        val revoked =
                            server.revokeAdvancement(
                                input.string("player"),
                                input.string("advancement"),
                                input.stringOrNull("criterion"),
                            )
                        done("Revoked $revoked criteria")
                    },
                "saved_merge_nbt" to
                    destructiveAction { server, input ->
                        server.mergeOfflinePlayerNbt(input.string("player"), input.string("snbt"))
                        done("Saved data merged")
                    },
                "saved_set_slot" to
                    destructiveAction { server, input ->
                        server.changeOfflineInventorySlot(
                            input.string("player"),
                            input.int("slot"),
                            input.decodeOrNull<ItemInfo>("item"),
                            input.boolean("ender_chest"),
                        )
                        done("Slot ${input.int("slot")} set")
                    },
            ),
    )
