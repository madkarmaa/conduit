package top.madkarma.conduit.service.world

import top.madkarma.conduit.mcp.*

val WORLD_INFO =
    ToolDefinition(
        name = "world_info",
        title = "World info",
        description =
            "info reads the time, weather, world spawn and world border of a dimension. A day " +
                "is 24000 ticks, with sunrise at 0 and noon at 6000. game_rules lists every game " +
                "rule with its value. height is the y of the first free block above the surface " +
                "of a column. chunk tells whether the chunk containing a column is loaded or " +
                "force-loaded, with its entity counts.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional(
                    "x",
                    integerSchema("Block x of the column. Required for height and chunk."),
                )
                optional(
                    "z",
                    integerSchema("Block z of the column. Required for height and chunk."),
                )
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
        defaultAction = "info",
        actions =
            mapOf(
                "info" to
                    readAction { server, input ->
                        json(server.describeWorld(input.stringOrNull("dimension")))
                    },
                "game_rules" to readAction { server, _ -> json(server.listGameRules()) },
                "height" to
                    readAction { server, input ->
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
                        val chunkX = Math.floorDiv(input.int("x"), 16)
                        val chunkZ = Math.floorDiv(input.int("z"), 16)
                        json(server.describeChunk(input.stringOrNull("dimension"), chunkX, chunkZ))
                    },
            ),
    )
