package top.madkarma.conduit.service.world

import top.madkarma.conduit.mcp.*

val GAME_RULES =
    ToolDefinition(
        name = "game_rules",
        title = "Game rules",
        description = "Lists every game rule with its value, or sets one.",
        input =
            input {
                optional("name", stringSchema("Rule name, e.g. \"advance_time\"."))
                optional("value", stringSchema("New value, e.g. \"false\" or \"3\"."))
            },
        actions =
            mapOf(
                "list" to readAction { server, _ -> json(server.listGameRules()) },
                "set" to
                    destructiveAction { server, input ->
                        input.require("name", "value")
                        server.changeGameRule(input.string("name"), input.string("value"))
                        done("${input.string("name")} = ${input.string("value")}")
                    },
            ),
    )

val WORLD_BORDER =
    ToolDefinition(
        name = "world_border",
        title = "Set world border",
        description =
            "Changes a dimension's world border. Fields left out stay unchanged. world_info " +
                "reads the current border.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional("center_x", numberSchema())
                optional("center_z", numberSchema())
                optional("size", numberSchema("Side length in blocks.", min = 1.0))
                optional(
                    "transition_ms",
                    integerSchema(
                        "Time to grow or shrink to size. 0 changes it instantly.",
                        min = 0,
                        default = 0,
                    ),
                )
                optional("damage_per_block", numberSchema(min = 0.0))
                optional("damage_safe_zone", numberSchema(min = 0.0))
                optional("warning_blocks", integerSchema(min = 0))
                optional("warning_time_ticks", integerSchema(min = 0))
            },
        actions =
            mapOf(
                "set" to
                    destructiveAction { server, input ->
                        val change =
                            WorldBorderChange(
                                dimension = input.stringOrNull("dimension"),
                                centerX = input.doubleOrNull("center_x"),
                                centerZ = input.doubleOrNull("center_z"),
                                size = input.doubleOrNull("size"),
                                transitionMs = input.long("transition_ms"),
                                damagePerBlock = input.doubleOrNull("damage_per_block"),
                                damageSafeZone = input.doubleOrNull("damage_safe_zone"),
                                warningBlocks = input.intOrNull("warning_blocks"),
                                warningTimeTicks = input.intOrNull("warning_time_ticks"),
                            )
                        server.changeWorldBorder(change)
                        done("World border updated")
                    },
            ),
    )

val EXPLODE =
    ToolDefinition(
        name = "explode",
        title = "Explode",
        description = "Creates an explosion. TNT has power 4, a creeper 3.",
        input =
            input {
                optional("dimension", DIMENSION)
                required("position", VEC3)
                optional("power", numberSchema(min = 0.0, default = 4.0))
                optional("fire", booleanSchema(default = false))
                optional(
                    "interaction",
                    enumSchema(
                        listOf("NONE", "BLOCK", "MOB", "TNT", "TRIGGER"),
                        "How the explosion affects blocks. NONE breaks no blocks.",
                        default = "TNT",
                    ),
                )
            },
        actions =
            mapOf(
                "explode" to
                    destructiveAction { server, input ->
                        server.explode(
                            input.stringOrNull("dimension"),
                            input.decode("position"),
                            input.float("power"),
                            input.boolean("fire"),
                            input.string("interaction"),
                        )
                        done("Exploded")
                    },
            ),
    )
