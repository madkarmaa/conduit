package top.madkarma.conduit.service.player

import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.server.broadcast

val EDIT_PLAYER =
    ToolDefinition(
        name = "edit_player",
        title = "Edit player",
        description =
            "Changes an online player: game_mode, food (level 0-20 and saturation), experience " +
                "(levels and points within the level), operator status, or kick disconnects them.",
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
                optional("reason", stringSchema("Kick message.", default = "Kicked by an operator"))
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
                "kick" to
                    destructiveAction { server, input ->
                        server.kick(input.string("player"), input.string("reason"))
                        done("Player kicked")
                    },
            ),
    )

val NOTIFY =
    ToolDefinition(
        name = "notify",
        title = "Notify players",
        description =
            "message sends a chat or action bar message to a player, or to everyone when no " +
                "player is given. title shows a title and subtitle to a player. sound plays a " +
                "sound to a player.",
        input =
            input {
                optional("player", PLAYER.described("Required for title and sound."))
                optional("message", TEXT.described("Message, or the title for title."))
                optional("subtitle", TEXT)
                optional(
                    "action_bar",
                    booleanSchema("Show message in the action bar.", default = false),
                )
                optional(
                    "sound",
                    stringSchema("Sound id, e.g. \"minecraft:entity.player.levelup\"."),
                )
                optional("position", VEC3.described("Sound position. Defaults to the player."))
                optional("volume", numberSchema(min = 0.0, default = 1.0))
                optional("pitch", numberSchema(min = 0.5, max = 2.0, default = 1.0))
            },
        actions =
            mapOf(
                "message" to
                    writeAction { server, input ->
                        input.require("message")
                        val message = input.string("message")
                        val overlay = input.boolean("action_bar")
                        val player = input.stringOrNull("player")

                        if (player == null) server.broadcast(message, overlay)
                        else server.sendMessage(player, message, overlay)
                        done("Message sent")
                    },
                "title" to
                    writeAction { server, input ->
                        input.require("player")
                        server.sendTitle(
                            input.string("player"),
                            input.stringOrNull("message"),
                            input.stringOrNull("subtitle"),
                        )
                        done("Title shown")
                    },
                "sound" to
                    writeAction { server, input ->
                        input.require("player", "sound")
                        server.playSound(
                            input.string("player"),
                            input.string("sound"),
                            input.decodeOrNull<Vector3>("position"),
                            input.float("volume"),
                            input.float("pitch"),
                        )
                        done("Sound played")
                    },
            ),
    )
