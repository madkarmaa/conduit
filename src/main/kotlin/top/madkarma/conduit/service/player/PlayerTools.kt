package top.madkarma.conduit.service.player

import java.time.Instant
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.mcp.*

val LIST_PLAYERS =
    ToolDefinition(
        name = "list_players",
        title = "List players",
        description =
            "online lists the online players with their game mode, position, health and food. " +
                "known lists every player with saved data, also offline ones. banned lists the " +
                "player and IP bans. whitelisted tells whether the whitelist is on, and who is " +
                "on it.",
        seesOtherPlayers = true,
        input = input {},
        defaultAction = "online",
        actions =
            mapOf(
                "online" to
                    readAction { server, _ ->
                        val players = server.listPlayers().players
                        lines(
                            "${players.size} players online",
                            players.map(::describeOnline),
                            dataOf("players" to encoded(players)),
                        )
                    },
                "known" to
                    readAction { server, _ ->
                        val players = server.listKnownPlayers()
                        lines(
                            "${players.size} known players",
                            players.map(::describeKnown),
                            dataOf("players" to encoded(players)),
                        )
                    },
                "banned" to readAction { server, _ -> bansReply(server.listBans()) },
                "whitelisted" to
                    readAction { server, _ ->
                        val whitelist = server.listWhitelist()
                        val state = if (whitelist.enabled) "on" else "off"
                        lines(
                            "Whitelist $state, ${whitelist.players.size} players",
                            whitelist.players,
                            encodedObject(whitelist),
                        )
                    },
            ),
    )

val GET_PLAYER =
    ToolDefinition(
        name = "get_player",
        title = "Get player",
        description =
            "info reads an online player, with the block or entity they look at. stats reads " +
                "the statistics of any player. advancements reads an online player's " +
                "advancements, with the criteria still missing. saved and saved_nbt read the " +
                "saved data of any player, also offline ones. The inventory tool reads " +
                "inventories.",
        input =
            input {
                required("player", PLAYER)
                optional(
                    "filter",
                    stringSchema(
                        "Substring for stats and advancements, such as \"mined\" or \"nether\".",
                        default = "",
                    ),
                )
                optional(
                    "status",
                    enumSchema(
                        listOf("all", "done", "undone"),
                        "For advancements.",
                        default = "all",
                    ),
                )
            },
        defaultAction = "info",
        actions =
            mapOf(
                "info" to
                    readAction { server, input ->
                        json(server.describePlayer(input.string("player")))
                    },
                "stats" to
                    readAction { server, input ->
                        val filter = input.string("filter")
                        json(server.playerStats(input.string("player")).filterKeys { filter in it })
                    },
                "advancements" to readAction { server, input -> server.advancementsReply(input) },
                "saved" to
                    readAction { server, input ->
                        json(server.offlinePlayer(input.string("player")))
                    },
                "saved_nbt" to
                    readAction { server, input ->
                        done(server.offlinePlayerNbt(input.string("player")))
                    },
            ),
    )

private fun describeOnline(player: PlayerInfo): String {
    val entity = player.entity
    val health = entity.health?.let { round(it.toDouble()) }
    return "${player.name} ${entity.uuid} ${player.gameMode} ${entity.dimension} ${position(entity.position)} " +
        "${health}hp food ${player.foodLevel}"
}

private fun describeKnown(player: KnownPlayer): String {
    val seen =
        if (player.online) " online" else " last played ${Instant.ofEpochMilli(player.lastPlayed)}"
    return "${player.name.ifEmpty { "?" }} ${player.uuid}$seen"
}

// A few missing criteria are worth naming, such as the last biomes of "Adventuring Time".
private const val NAMED_MISSING = 8

private fun MinecraftServer.advancementsReply(input: ToolInput): Reply {
    val advancements =
        advancementsOf(input.string("player"), input.string("filter"), input.string("status"))
    val rows = advancements.map {
        val progress = if (it.done) "done" else "${it.criteriaDone}/${it.criteriaTotal}"
        val missing =
            if (it.missing.size in 1..NAMED_MISSING) ", missing ${it.missing.joinToString(", ")}"
            else ""
        "${it.id} \"${it.title}\": $progress$missing"
    }
    return lines(
        "${advancements.size} advancements",
        rows,
        dataOf("advancements" to encoded(advancements)),
    )
}

private fun bansReply(bans: BanList): Reply {
    val rows =
        (bans.players + bans.ips).map {
            "${it.target}: ${it.reason}, by ${it.source} until ${it.expires ?: "forever"}"
        }
    return lines(
        "${bans.players.size} player bans, ${bans.ips.size} IP bans",
        rows,
        encodedObject(bans),
    )
}
