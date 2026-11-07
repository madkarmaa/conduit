package top.madkarma.conduit.service.player

import top.madkarma.conduit.mcp.*

val MODERATION =
    ToolDefinition(
        name = "moderation",
        title = "Moderation",
        description = "kick disconnects an online player with a message.",
        input =
            input {
                required("player", PLAYER)
                optional("reason", stringSchema("Kick message.", default = "Kicked by an operator"))
            },
        actions =
            mapOf(
                "kick" to
                    destructiveAction { server, input ->
                        server.kick(input.string("player"), input.string("reason"))
                        done("Player kicked")
                    },
            ),
    )
