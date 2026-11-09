package top.madkarma.conduit.service.player

import top.madkarma.conduit.mcp.*

val MODERATION =
    ToolDefinition(
        name = "moderation",
        title = "Moderation",
        description =
            "kick disconnects a player. ban bans a player, also an offline one, for some " +
                "minutes or for good. pardon lifts a ban. whitelist_add, whitelist_remove and " +
                "whitelist_enabled change the whitelist.",
        input =
            input {
                optional(
                    "player",
                    PLAYER.described("Required for every action but whitelist_enabled."),
                )
                optional("reason", stringSchema("Kick or ban message."))
                optional(
                    "minutes",
                    integerSchema("How long ban lasts. Leave it out for good.", min = 1),
                )
                optional("enabled", booleanSchema("Required for whitelist_enabled."))
            },
        actions =
            mapOf(
                "kick" to
                    destructiveAction { server, input ->
                        val reason = input.stringOrNull("reason") ?: "Kicked by an operator"
                        server.kick(input.string("player"), reason)
                        done("Player kicked")
                    },
                "ban" to
                    destructiveAction { server, input ->
                        val reason = input.stringOrNull("reason") ?: "Banned by an operator"
                        server.ban(input.string("player"), reason, input.intOrNull("minutes"))
                        done("Player banned")
                    },
                "pardon" to
                    writeAction { server, input ->
                        server.pardon(input.string("player"))
                        done("Ban lifted")
                    },
                "whitelist_add" to
                    writeAction { server, input ->
                        server.addToWhitelist(input.string("player"))
                        done("Added to the whitelist")
                    },
                "whitelist_remove" to
                    writeAction { server, input ->
                        server.removeFromWhitelist(input.string("player"))
                        done("Removed from the whitelist")
                    },
                "whitelist_enabled" to
                    destructiveAction { server, input ->
                        val enabled = input.boolean("enabled")
                        server.changeWhitelistEnabled(enabled)
                        done(if (enabled) "Whitelist on" else "Whitelist off")
                    },
            ),
    )
