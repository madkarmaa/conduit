package top.madkarma.conduit.service.world

import top.madkarma.conduit.mcp.*

private val COLUMN_X = integerSchema("Block x of the column. Required for height and chunks.")
private val COLUMN_Z = integerSchema("Block z of the column. Required for height and chunks.")

val TERRAIN =
    ToolDefinition(
        name = "terrain",
        title = "Terrain",
        description =
            "height: y of the first free block above the surface of a column. chunk: whether the " +
                "chunk containing a column is loaded or force-loaded, with its entity counts.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional("x", COLUMN_X)
                optional("z", COLUMN_Z)
                optional(
                    "heightmap",
                    enumSchema(
                        listOf(
                            "WORLD_SURFACE",
                            "MOTION_BLOCKING",
                            "MOTION_BLOCKING_NO_LEAVES",
                            "OCEAN_FLOOR",
                        ),
                        default = "MOTION_BLOCKING",
                    ),
                )
            },
        actions =
            mapOf(
                "height" to
                    readAction { server, input ->
                        input.require("x", "z")
                        val y =
                            server.heightAt(
                                input.stringOrNull("dimension"),
                                input.int("x"),
                                input.int("z"),
                                input.string("heightmap"),
                            )
                        done("y=$y")
                    },
                "chunk" to
                    readAction { server, input ->
                        input.require("x", "z")
                        val chunkX = Math.floorDiv(input.int("x"), 16)
                        val chunkZ = Math.floorDiv(input.int("z"), 16)
                        json(server.describeChunk(input.stringOrNull("dimension"), chunkX, chunkZ))
                    },
            ),
    )

val WORLD_INFO =
    ToolDefinition(
        name = "world_info",
        title = "World info",
        description =
            "Time, weather, world spawn and world border of a dimension. A day is 24000 ticks, " +
                "with sunrise at 0 and noon at 6000.",
        input = input { optional("dimension", DIMENSION) },
        actions =
            mapOf(
                "get" to
                    readAction { server, input ->
                        json(server.describeWorld(input.stringOrNull("dimension")))
                    },
            ),
    )

val WORLD_SETTINGS =
    ToolDefinition(
        name = "world_settings",
        title = "World settings",
        description =
            "time sets the time of day from exactly one of time, add or preset. weather sets the " +
                "weather. spawn sets the world spawn. force_chunk force-loads or releases the " +
                "chunk that contains a column, so entities can spawn or move there.",
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
                optional("x", COLUMN_X)
                optional("z", COLUMN_Z)
                optional("forced", booleanSchema("Required for force_chunk."))
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
                        input.require("weather")
                        val weather = input.string("weather")
                        server.changeWeather(
                            Weather.valueOf(weather.uppercase()),
                            input.int("duration_ticks"),
                        )
                        done("Weather set to $weather")
                    },
                "spawn" to
                    writeAction { server, input ->
                        input.require("pos")
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
                        input.require("x", "z", "forced")
                        val forced = input.boolean("forced")
                        val chunkX = Math.floorDiv(input.int("x"), 16)
                        val chunkZ = Math.floorDiv(input.int("z"), 16)
                        server.changeChunkForced(
                            input.stringOrNull("dimension"),
                            chunkX,
                            chunkZ,
                            forced,
                        )
                        done(if (forced) "Chunk force-loaded" else "Chunk released")
                    },
            ),
    )
