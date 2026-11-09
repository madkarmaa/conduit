package top.madkarma.conduit.service.player

import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.server.broadcast

val NOTIFY =
    ToolDefinition(
        name = "notify",
        title = "Notify players",
        description =
            "message sends chat or action bar text to a player, or to everyone. title " +
                "shows a title and subtitle. sound plays a sound. bossbar creates or changes " +
                "a boss bar, and remove_bossbar removes it.",
        input =
            input {
                optional("player", PLAYER.described("Required for title and sound."))
                optional(
                    "message",
                    TEXT.described("Message, the title for title, or the boss bar's name."),
                )
                optional("subtitle", TEXT)
                optional(
                    "action_bar",
                    booleanSchema("Show message in the action bar.", default = false),
                )
                optional(
                    "sound",
                    stringSchema("Sound id, such as \"minecraft:entity.player.levelup\"."),
                )
                optional("position", VEC3.described("Sound position. Defaults to the player."))
                optional("volume", numberSchema(min = 0.0, default = 1.0))
                optional("pitch", numberSchema(min = 0.5, max = 2.0, default = 1.0))
                bossBarFields()
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
                "bossbar" to
                    writeAction { server, input ->
                        val created = server.updateBossBar(bossBarChange(input))
                        done(if (created) "Boss bar created" else "Boss bar updated")
                    },
                "remove_bossbar" to
                    writeAction { server, input ->
                        server.removeBossBar(input.string("bar"))
                        done("Boss bar removed")
                    },
            ),
    )

private fun InputBuilder.bossBarFields() {
    optional(
        "bar",
        stringSchema("Boss bar id, such as \"conduit:race\". For the boss bar actions."),
    )
    optional(
        "color",
        enumSchema(listOf("pink", "blue", "red", "green", "yellow", "purple", "white")),
    )
    optional(
        "style",
        enumSchema(listOf("progress", "notched_6", "notched_10", "notched_12", "notched_20")),
    )
    optional("value", integerSchema("Boss bar progress, out of max.", min = 0))
    optional("max", integerSchema(min = 1))
    optional("visible", booleanSchema())
    optional("players", arraySchema(PLAYER, "Who sees the boss bar. Replaces the earlier ones."))
}

private fun bossBarChange(input: ToolInput) =
    BossBarChange(
        id = input.string("bar"),
        name = input.stringOrNull("message"),
        color = input.stringOrNull("color"),
        style = input.stringOrNull("style"),
        value = input.intOrNull("value"),
        max = input.intOrNull("max"),
        visible = input.booleanOrNull("visible"),
        players = if (input.has("players")) input.strings("players") else null,
    )
