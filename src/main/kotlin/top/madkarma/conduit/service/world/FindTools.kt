package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.*

private const val MAX_RADIUS = 48

val FIND =
    ToolDefinition(
        name = "find",
        title = "Find",
        description =
            "blocks finds the blocks matching a block id, state or #tag in a cuboid (from and " +
                "to) or within radius of center, nearest to the center first. structure, biome " +
                "and poi find the nearest structure, biome or point of interest (such as " +
                "\"minecraft:home\" for beds) from center, like /locate. Each takes an id or a " +
                "#tag. Points of interest are only known in chunks loaded since they were placed.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional(
                    "block",
                    stringSchema("Block id, state or #tag, e.g. \"#minecraft:logs\". For blocks."),
                )
                optional(
                    "structure",
                    stringSchema("e.g. \"minecraft:village_plains\" or \"#minecraft:village\"."),
                )
                optional("biome", stringSchema("e.g. \"minecraft:cherry_grove\"."))
                optional(
                    "poi",
                    stringSchema("e.g. \"minecraft:home\" or \"#minecraft:acquirable_job_site\"."),
                )
                optional(
                    "center",
                    BLOCK_POS.described(
                        "Where the search starts. Required for structure, biome and poi.",
                    ),
                )
                optional(
                    "radius",
                    integerSchema("For blocks, around center.", min = 0, max = MAX_RADIUS),
                )
                optional("from", BLOCK_POS.described("Cuboid corner for blocks."))
                optional("to", BLOCK_POS.described("Opposite cuboid corner for blocks."))
                optional(
                    "loaded_only",
                    booleanSchema(
                        "blocks skips unloaded chunks instead of loading them.",
                        default = false,
                    ),
                )
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "blocks" to readAction { server, input -> server.blocksReply(input) },
                "structure" to
                    readAction { server, input ->
                        located(
                            server.locateStructure(
                                input.stringOrNull("dimension"),
                                input.string("structure"),
                                center(input),
                            ),
                        )
                    },
                "biome" to
                    readAction { server, input ->
                        located(
                            server.locateBiome(
                                input.stringOrNull("dimension"),
                                input.string("biome"),
                                center(input),
                            ),
                        )
                    },
                "poi" to
                    readAction { server, input ->
                        located(
                            server.locatePoi(
                                input.stringOrNull("dimension"),
                                input.string("poi"),
                                center(input),
                            ),
                        )
                    },
            ),
    )

private fun MinecraftServer.blocksReply(input: ToolInput): Reply {
    val search =
        findBlocks(
            input.stringOrNull("dimension"),
            input.string("block"),
            area(input),
            input.boolean("loaded_only"),
            input.int("limit"),
        )

    return lines(
        "${search.total} matching blocks, nearest first:",
        search.matches.map { "${it.pos.x} ${it.pos.y} ${it.pos.z} ${it.state}" },
        encodedObject(search),
    )
}

// A cuboid searches around its middle, and a radius around its center.
private fun area(input: ToolInput): BlockArea {
    val radius = input.intOrNull("radius")
    if (radius != null) {
        val center = input.decode<BlockCoordinates>("center")
        val from = BlockCoordinates(center.x - radius, center.y - radius, center.z - radius)
        val to = BlockCoordinates(center.x + radius, center.y + radius, center.z + radius)
        return BlockArea(from, to, center.toMc())
    }

    if (!input.has("from") || !input.has("to"))
        invalidInput("blocks needs from and to, or center and radius")
    val from = input.decode<BlockCoordinates>("from")
    val to = input.decode<BlockCoordinates>("to")
    val middle = BlockPos((from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2)
    return BlockArea(from, to, middle)
}

private fun center(input: ToolInput): BlockPos = input.decode<BlockCoordinates>("center").toMc()

private fun located(found: Located): Reply =
    Reply(
        "Nearest ${found.id} at ${found.pos.x} ${found.pos.y} ${found.pos.z}, " +
            "${found.distance} blocks away",
        encodedObject(found),
    )
