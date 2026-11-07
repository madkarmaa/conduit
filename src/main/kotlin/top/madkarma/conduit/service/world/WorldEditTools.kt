package top.madkarma.conduit.service.world

import top.madkarma.conduit.mcp.*

val EDIT_WORLD =
    ToolDefinition(
        name = "edit_world",
        title = "Edit world",
        description =
            "time sets the time of day from exactly one of time, add or preset. weather sets the " +
                "weather. spawn sets the world spawn. force_chunk force-loads or releases the " +
                "chunk that contains a column, so entities can spawn or move there. border " +
                "changes a dimension's world border and keeps the fields left out. game_rule " +
                "sets a game rule. explode creates an explosion: TNT has power 4, a creeper 3.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional("time", integerSchema("Absolute time of day in ticks.", min = 0))
                optional("add", integerSchema("Ticks to add to the current time."))
                optional("preset", enumSchema(listOf("day", "noon", "night", "midnight")))
                optional("weather", enumSchema(listOf("clear", "rain", "thunder")))
                optional(
                    "duration_ticks",
                    integerSchema("0 means a random duration.", min = 0, default = 0),
                )
                optional("pos", BLOCK_POS.described("Required for spawn."))
                optional("yaw", numberSchema(default = 0.0))
                optional("pitch", numberSchema(default = 0.0))
                optional("x", integerSchema("Block x of the column. Required for force_chunk."))
                optional("z", integerSchema("Block z of the column. Required for force_chunk."))
                optional("forced", booleanSchema("Required for force_chunk."))
                borderFields()
                optional("name", stringSchema("Rule name, e.g. \"advance_time\"."))
                optional("value", stringSchema("New rule value, e.g. \"false\" or \"3\"."))
                explosionFields()
            },
        actions =
            mapOf(
                "time" to
                    writeAction { server, input ->
                        server.changeTime(
                            input.stringOrNull("dimension"),
                            input.longOrNull("time"),
                            input.longOrNull("add"),
                            input.stringOrNull("preset"),
                        )
                        done("Time set")
                    },
                "weather" to
                    writeAction { server, input ->
                        val weather = input.string("weather")
                        server.changeWeather(
                            Weather.valueOf(weather.uppercase()),
                            input.int("duration_ticks"),
                        )
                        done("Weather set to $weather")
                    },
                "spawn" to
                    writeAction { server, input ->
                        server.changeSpawnPoint(
                            input.stringOrNull("dimension"),
                            input.decode("pos"),
                            input.float("yaw"),
                            input.float("pitch"),
                        )
                        done("Spawn point set")
                    },
                "force_chunk" to
                    writeAction { server, input ->
                        val forced = input.boolean("forced")
                        server.changeChunkForced(
                            input.stringOrNull("dimension"),
                            Math.floorDiv(input.int("x"), 16),
                            Math.floorDiv(input.int("z"), 16),
                            forced,
                        )
                        done(if (forced) "Chunk force-loaded" else "Chunk released")
                    },
                "border" to
                    destructiveAction { server, input ->
                        server.changeWorldBorder(borderChange(input))
                        done("World border updated")
                    },
                "game_rule" to
                    destructiveAction { server, input ->
                        server.changeGameRule(input.string("name"), input.string("value"))
                        done("${input.string("name")} = ${input.string("value")}")
                    },
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

private fun InputBuilder.borderFields() {
    optional("center_x", numberSchema("Border center, for border."))
    optional("center_z", numberSchema("Border center, for border."))
    optional("size", numberSchema("Border side length in blocks.", min = 1.0))
    optional(
        "transition_ms",
        integerSchema(
            "Time for the border to grow or shrink to size. 0 changes it instantly.",
            min = 0,
            default = 0,
        ),
    )
    optional("damage_per_block", numberSchema(min = 0.0))
    optional("damage_safe_zone", numberSchema(min = 0.0))
    optional("warning_blocks", integerSchema(min = 0))
    optional("warning_time_ticks", integerSchema(min = 0))
}

private fun InputBuilder.explosionFields() {
    optional("position", VEC3.described("Explosion center. Required for explode."))
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
}

private fun borderChange(input: ToolInput) =
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
