package top.madkarma.conduit.service.world

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*

private const val MAX_HEIGHT_POINTS = 256

val WORLD_INFO =
    ToolDefinition(
        name = "world_info",
        title = "World info",
        description =
            "info reads the time, weather, spawn and world border. A day is 24000 ticks, " +
                "and noon is 6000. game_rules lists the game rules. height returns the y of " +
                "the first free block above a column, or above each column in points. Under " +
                "a ceiling, such as in the Nether, it lists the floors to stand on. chunk " +
                "tells whether a column's chunk is loaded or forced, with entity counts.",
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
                    "points",
                    arraySchema(
                        objectSchema {
                            required("x", integerSchema())
                            required("z", integerSchema())
                        },
                        "Columns for height, instead of x and z.",
                        minItems = 1,
                        maxItems = MAX_HEIGHT_POINTS,
                    ),
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
                "height" to readAction(::heights),
                "chunk" to
                    readAction { server, input ->
                        val chunkX = Math.floorDiv(input.int("x"), 16)
                        val chunkZ = Math.floorDiv(input.int("z"), 16)
                        json(server.describeChunk(input.stringOrNull("dimension"), chunkX, chunkZ))
                    },
            ),
    )

private fun heights(server: MinecraftServer, input: ToolInput): Reply {
    val dimension = input.stringOrNull("dimension")
    val heightmap = input.string("heightmap")
    val points = input.decodeOrNull<List<Column>>("points")

    if (points == null) {
        val height = server.columnHeight(dimension, input.int("x"), input.int("z"), heightmap)
        return done(if (height.first().isLetter()) height else "y=$height")
    }
    if (input.has("x") || input.has("z")) invalidInput("Set either x and z, or points")

    val lines = points.map {
        "${it.x} ${it.z} ${server.columnHeight(dimension, it.x, it.z, heightmap)}"
    }
    return done(lines.joinToString("\n"))
}
