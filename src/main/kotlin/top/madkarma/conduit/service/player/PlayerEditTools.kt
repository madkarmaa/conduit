package top.madkarma.conduit.service.player

import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.inventory.SLOT

val EDIT_PLAYER =
    ToolDefinition(
        name = "edit_player",
        title = "Edit player",
        description =
            "Changes an online player: game_mode, food (level 0-20 and saturation), experience " +
                "(levels and points within the level) and operator status. saved_merge_nbt and " +
                "saved_set_slot edit the saved data of an offline player, which applies at their " +
                "next login. get_player reads saved data. grant_advancement and " +
                "revoke_advancement grant or revoke an advancement, or one of its criteria.",
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
                        "Advancement id, such as \"minecraft:story/mine_diamond\". For the advancement actions.",
                    ),
                )
                optional("criterion", stringSchema("One criterion. Leave it out for all of them."))
                optional("slot", SLOT.described("Required for saved_set_slot."))
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
                        )
                        done("Slot ${input.int("slot")} set")
                    },
            ),
    )
