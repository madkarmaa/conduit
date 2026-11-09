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
            "info reads the time, weather, world spawn and world border of a dimension. A day " +
                "is 24000 ticks, with sunrise at 0 and noon at 6000. game_rules lists every game " +
                "rule with its value. height is the y of the first free block above the surface " +
                "of a column, or of each column in points, which reads many columns in one call. " +
                "chunk tells whether the chunk containing a column is loaded or " +
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
                    "points",
                    arraySchema(
                        objectSchema {
                            required("x", integerSchema())
                            required("z", integerSchema())
                        },
                        "Columns for height, instead of x and z. The reply has one x z y line " +
                            "per column, in this order.",
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
        return done("y=${server.heightAt(dimension, input.int("x"), input.int("z"), heightmap)}")
    }
    if (input.has("x") || input.has("z")) invalidInput("Set either x and z, or points")

    val lines = points.map {
        "${it.x} ${it.z} ${server.heightAt(dimension, it.x, it.z, heightmap)}"
    }
    return done(lines.joinToString("\n"))
}
