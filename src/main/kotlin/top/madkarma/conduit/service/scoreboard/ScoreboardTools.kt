package top.madkarma.conduit.service.scoreboard

import top.madkarma.conduit.mcp.*

val SCOREBOARD =
    ToolDefinition(
        name = "scoreboard",
        title = "Scoreboard and teams",
        description =
            "objectives lists the objectives. scores reads the scores of a holder, which is a " +
                "player name or entity UUID, of an objective, or both. add_objective, " +
                "remove_objective, set_score, add_score and reset_score change them, and display " +
                "shows an objective " +
                "in a slot such as sidebar, or clears the slot. teams lists the teams with their " +
                "members. create_team, modify_team and remove_team change teams, and join_team " +
                "and leave_team move members.",
        seesOtherPlayers = true,
        input =
            input {
                optional("objective", stringSchema("Objective name."))
                optional("holder", stringSchema("Player name or entity UUID that holds a score."))
                optional("value", integerSchema("For set_score and add_score."))
                optional(
                    "criterion",
                    stringSchema(
                        "For add_objective, e.g. \"dummy\" or \"deathCount\".",
                        default = "dummy",
                    ),
                )
                optional(
                    "display_name",
                    TEXT.described("For add_objective, create_team and modify_team."),
                )
                optional(
                    "slot",
                    stringSchema(
                        "For display: \"sidebar\", \"list\", \"below_name\" or " +
                            "\"sidebar.team.red\".",
                    ),
                )
                optional("team", stringSchema("Team name."))
                optional("members", arraySchema(stringSchema(), "Player names or entity UUIDs."))
                teamFields()
            },
        actions =
            mapOf(
                "objectives" to
                    readAction { server, _ -> objectivesReply(server.listObjectives()) },
                "scores" to
                    readAction { server, input ->
                        val scores =
                            server.readScores(
                                input.stringOrNull("objective"),
                                input.stringOrNull("holder"),
                            )
                        lines(
                            "${scores.size} scores",
                            scores.map { "${it.holder} ${it.objective}: ${it.value}" },
                            dataOf("scores" to encoded(scores)),
                        )
                    },
                "add_objective" to
                    writeAction { server, input ->
                        server.addObjective(
                            input.string("objective"),
                            input.string("criterion"),
                            input.stringOrNull("display_name"),
                        )
                        done("Objective added")
                    },
                "remove_objective" to
                    destructiveAction { server, input ->
                        server.removeObjective(input.string("objective"))
                        done("Objective removed")
                    },
                "set_score" to
                    writeAction { server, input -> server.scoreReply(input, add = false) },
                "add_score" to
                    writeAction { server, input -> server.scoreReply(input, add = true) },
                "reset_score" to
                    writeAction { server, input ->
                        server.resetScore(input.string("holder"), input.stringOrNull("objective"))
                        done("Score reset")
                    },
                "display" to
                    writeAction { server, input ->
                        server.displayObjective(
                            input.string("slot"),
                            input.stringOrNull("objective"),
                        )
                        done("Display updated")
                    },
                "teams" to readAction { server, _ -> teamsReply(server.listTeams()) },
                "create_team" to
                    writeAction { server, input ->
                        server.createTeam(input.string("team"), teamChange(input))
                        done("Team created")
                    },
                "modify_team" to
                    writeAction { server, input ->
                        server.modifyTeam(input.string("team"), teamChange(input))
                        done("Team updated")
                    },
                "remove_team" to
                    destructiveAction { server, input ->
                        server.removeTeam(input.string("team"))
                        done("Team removed")
                    },
                "join_team" to
                    writeAction { server, input ->
                        server.joinTeam(input.string("team"), input.strings("members"))
                        done("Members joined ${input.string("team")}")
                    },
                "leave_team" to
                    writeAction { server, input ->
                        server.leaveTeams(input.strings("members"))
                        done("Members left their teams")
                    },
            ),
    )
