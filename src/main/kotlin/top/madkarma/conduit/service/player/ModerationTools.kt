package top.madkarma.conduit.service.player

import net.minecraft.server.MinecraftServer
import net.minecraft.server.players.NameAndId
import top.madkarma.conduit.mcp.*

val MODERATION =
    ToolDefinition(
        name = "moderation",
        title = "Moderation",
        description =
            "kick disconnects a player. ban bans a player, also an offline one, for some " +
                "minutes or for good. pardon lifts a ban. whitelist_add and whitelist_remove " +
                "change the whitelist, and server_settings turns it on. Use these instead of " +
                "the vanilla /ban, /whitelist and /op commands, which look names up online and " +
                "can store a UUID that players joining an offline server never have. A UUID " +
                "as player names one exact entry, such as a stale one in the whitelist.",
        input =
            input {
                required("player", PLAYER)
                optional("reason", stringSchema("Kick or ban message."))
                optional(
                    "minutes",
                    integerSchema("How long ban lasts. Leave it out for good.", min = 1),
                )
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
                        val banned =
                            server.ban(input.string("player"), reason, input.intOrNull("minutes"))
                        done("Banned ${describe(banned)}")
                    },
                "pardon" to
                    writeAction { server, input ->
                        server.pardon(input.string("player"))
                        done("Ban lifted")
                    },
                "whitelist_add" to
                    writeAction { server, input ->
                        val added = server.addToWhitelist(input.string("player"))
                        done("Added ${describe(added)} to the whitelist${server.caseNote(added)}")
                    },
                "whitelist_remove" to
                    writeAction { server, input ->
                        val removed = server.removeFromWhitelist(input.string("player"))
                        done("Removed ${describe(removed)} from the whitelist")
                    },
            ),
    )

// A name in another case makes another offline UUID, so the player would not match the entry.
// Only a name the server has not seen yet becomes a UUID this way. A known player keeps theirs.
private fun MinecraftServer.caseNote(profile: NameAndId): String =
    if (joinsWithOfflineIds() && profile.hasName() && lastKnownName(profile.id()).isEmpty()) {
        ". Players join this server with offline UUIDs, which depend on the exact case of the " +
            "name."
    } else {
        ""
    }
