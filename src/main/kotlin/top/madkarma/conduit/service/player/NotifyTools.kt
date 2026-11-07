package top.madkarma.conduit.service.player

import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.server.broadcast

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
