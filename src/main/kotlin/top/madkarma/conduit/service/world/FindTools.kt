package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.inventory.findItems

private const val MAX_RADIUS = 48

// Searching items only reads block entities, so it covers more ground than blocks.
private const val MAX_ITEM_CHUNKS = 1024

val FIND =
    ToolDefinition(
        name = "find",
        title = "Find",
        description =
            "blocks finds the blocks that match a block id, state or #tag, in a cuboid between " +
                "from and to or within radius of center, nearest to the center first. items " +
                "finds the containers that hold an item id or #tag the same way, and counts the " +
                "items inside shulker boxes and bundles. structure, biome and poi find the " +
                "nearest structure, biome or point of interest from center, like /locate, such " +
                "as \"minecraft:home\" for beds. Each takes an id or a #tag. The game only " +
                "knows the points of interest of chunks loaded since they were placed.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional(
                    "block",
                    stringSchema("Block id, state or #tag, e.g. \"#minecraft:logs\". For blocks."),
                )
                optional("item", stringSchema("Item id or #tag. For items."))
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
                    integerSchema(
                        "For blocks and items, around center.",
                        min = 0,
                        max = MAX_RADIUS,
                    ),
                )
                optional("from", BLOCK_POS.described("Cuboid corner for blocks and items."))
                optional("to", BLOCK_POS.described("Opposite cuboid corner."))
                optional(
                    "loaded_only",
                    booleanSchema(
                        "blocks and items skip unloaded chunks instead of loading them.",
                        default = false,
                    ),
                )
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "blocks" to readAction { server, input -> server.blocksReply(input) },
                "items" to readAction { server, input -> server.itemsReply(input) },
                "structure" to
                    readAction { server, input ->
                        locatedReply(
                            server.locateStructure(
                                input.stringOrNull("dimension"),
                                input.string("structure"),
                                center(input),
                            ),
                        )
                    },
                "biome" to
                    readAction { server, input ->
                        locatedReply(
                            server.locateBiome(
                                input.stringOrNull("dimension"),
                                input.string("biome"),
                                center(input),
                            ),
                        )
                    },
                "poi" to
                    readAction { server, input ->
                        locatedReply(
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
            searchArea(input),
            input.boolean("loaded_only"),
            input.int("limit"),
        )

    return lines(
        "${search.total} matching blocks, nearest first:",
        search.matches.map { "${it.pos.x} ${it.pos.y} ${it.pos.z} ${it.state}" },
        encodedObject(search),
    )
}

private fun MinecraftServer.itemsReply(input: ToolInput): Reply {
    val area = searchArea(input)
    if (area.chunkCount > MAX_ITEM_CHUNKS) {
        invalidInput("items searches up to $MAX_ITEM_CHUNKS chunks, not ${area.chunkCount}")
    }

    val search =
        findItems(
            input.stringOrNull("dimension"),
            input.string("item"),
            area.min,
            area.max,
            area.center,
            input.boolean("loaded_only"),
            input.int("limit"),
        )
    return lines(
        "${search.total} matching items, nearest containers first:",
        search.containers.map { "${it.pos.x} ${it.pos.y} ${it.pos.z} ${it.block}: ${it.count}" },
        encodedObject(search),
    )
}

private fun center(input: ToolInput): BlockPos = input.decode<BlockCoordinates>("center").toMc()

private fun locatedReply(found: Located): Reply =
    Reply(
        "Nearest ${found.id} at ${found.pos.x} ${found.pos.y} ${found.pos.z}, " +
            "${found.distance} blocks away",
        encodedObject(found),
    )
