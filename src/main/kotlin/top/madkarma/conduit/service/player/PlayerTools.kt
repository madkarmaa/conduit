package top.madkarma.conduit.service.player

import java.time.Instant
import top.madkarma.conduit.mcp.*

val LIST_PLAYERS =
    ToolDefinition(
        name = "list_players",
        title = "List players",
        description =
            "online: online players with game mode, position, health and food. known: every " +
                "player with saved data, also offline ones.",
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
            ),
    )

val GET_PLAYER =
    ToolDefinition(
        name = "get_player",
        title = "Get player",
        description =
            "info, inventory and ender_chest read an online player. Inventory slots: 0-8 hotbar, " +
                "9-35 main, 36-39 armor (feet, legs, chest, head), 40 offhand. stats reads the " +
                "statistics of any player, filtered by a substring. saved and saved_nbt read the " +
                "saved data of any player, also offline ones.",
        input =
            input {
                required("player", PLAYER)
                optional(
                    "stats_filter",
                    stringSchema("e.g. \"mined\" or \"diamond\".", default = ""),
                )
            },
        defaultAction = "info",
        actions =
            mapOf(
                "info" to
                    readAction { server, input ->
                        json(server.describePlayer(input.string("player")))
                    },
                "inventory" to
                    readAction { server, input ->
                        val inventory = server.inventoryOf(input.string("player"))
                        describeInventory(
                            "Selected hotbar slot ${inventory.selectedSlot}; non-empty slots:",
                            inventory,
                        )
                    },
                "ender_chest" to
                    readAction { server, input ->
                        describeInventory(
                            "Non-empty ender chest slots:",
                            server.enderChestOf(input.string("player")),
                        )
                    },
                "stats" to
                    readAction { server, input ->
                        val filter = input.string("stats_filter")
                        json(server.playerStats(input.string("player")).filterKeys { filter in it })
                    },
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

internal fun describeInventory(header: String, inventory: InventoryContents): Reply =
    lines(
        header,
        inventory.slots.map { (slot, item) ->
            val components = if (item.components.isEmpty()) "" else " ${item.components}"
            "$slot: ${item.count} ${item.id}$components"
        },
        dataOf("slots" to encoded(inventory.slots)),
    )
