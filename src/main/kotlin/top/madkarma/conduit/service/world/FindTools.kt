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
            "blocks finds blocks that match an id, state or #tag, in a cuboid or within " +
                "radius of center, nearest first. items finds the containers that hold an " +
                "item, counting shulker boxes and bundles. structure, biome and poi find the " +
                "nearest one from center, like /locate. Points of interest only exist in " +
                "chunks loaded since they were placed. spot finds safe places for a player to " +
                "stand, nearest first.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional(
                    "block",
                    stringSchema(
                        "Block id, state or #tag, such as \"#minecraft:logs\". For blocks.",
                    ),
                )
                optional("item", stringSchema("Item id or #tag. For items."))
                optional(
                    "structure",
                    stringSchema("Structure id or #tag, such as \"#minecraft:village\"."),
                )
                optional(
                    "biome",
                    stringSchema("Biome id or #tag, such as \"minecraft:cherry_grove\"."),
                )
                optional(
                    "poi",
                    stringSchema(
                        "Point of interest type or #tag, such as \"minecraft:home\" for beds.",
                    ),
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
                        "For blocks, items and spot, around center.",
                        min = 0,
                        max = MAX_RADIUS,
                    ),
                )
                optional("from", BLOCK_POS.described("Cuboid corner for blocks, items and spot."))
                optional("to", BLOCK_POS.described("Opposite cuboid corner."))
                optional(
                    "loaded_only",
                    booleanSchema(
                        "blocks and items skip unloaded chunks instead of loading them.",
                        default = false,
                    ),
                )
                optional(
                    "outside",
                    objectSchema("spot leaves out this box, such as a structure's.") {
                        required("from", BLOCK_POS)
                        required("to", BLOCK_POS)
                    },
                )
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "blocks" to readAction { server, input -> server.blocksReply(input) },
                "items" to readAction { server, input -> server.itemsReply(input) },
                "spot" to readAction { server, input -> server.spotsReply(input) },
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
