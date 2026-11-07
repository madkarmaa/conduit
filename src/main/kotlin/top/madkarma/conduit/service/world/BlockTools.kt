package top.madkarma.conduit.service.world

import kotlin.math.abs
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*

// Drawn regions print one character per block, so they stay small enough to read.
private const val MAX_DRAWN_VOLUME = 8192

val GET_BLOCK =
    ToolDefinition(
        name = "get_block",
        title = "Get block",
        description = "Block state, biome and block entity SNBT (chest, sign, ...) at a position.",
        input =
            input {
                optional("dimension", DIMENSION)
                required("pos", BLOCK_POS)
            },
        actions =
            mapOf(
                "get" to
                    readAction { server, input ->
                        json(
                            server.describeBlock(
                                input.stringOrNull("dimension"),
                                input.decode("pos"),
                            ),
                        )
                    },
            ),
    )

val EDIT_BLOCKS =
    ToolDefinition(
        name = "edit_blocks",
        title = "Edit blocks",
        description =
            "set places block states at positions in one tick. fill fills a cuboid, corners " +
                "inclusive. break breaks a block like a player. merge_nbt merges SNBT into a " +
                "block entity.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional(
                    "changes",
                    arraySchema(
                        objectSchema {
                            required("pos", BLOCK_POS)
                            required("state", BLOCK_STATE)
                        },
                        "Required for set.",
                        minItems = 1,
                    ),
                )
                optional("from", BLOCK_POS.described("Fill corner."))
                optional("to", BLOCK_POS.described("Opposite fill corner."))
                optional("state", BLOCK_STATE.described("Required for fill."))
                optional(
                    "replace",
                    stringSchema("fill only replaces blocks matching this block id or state."),
                )
                optional("pos", BLOCK_POS.described("Required for break and merge_nbt."))
                optional(
                    "drop_items",
                    booleanSchema("break drops the block's items.", default = true),
                )
                optional("snbt", SNBT.described("Required for merge_nbt."))
                optional("no_updates", NO_UPDATES)
            },
        actions =
            mapOf(
                "set" to
                    writeAction { server, input ->
                        input.require("changes")
                        val changes = input.decode<List<BlockChange>>("changes")
                        val changed =
                            server.writeBlocks(
                                input.stringOrNull("dimension"),
                                changes,
                                input.boolean("no_updates"),
                            )
                        done("Changed $changed blocks")
                    },
                "fill" to
                    destructiveAction { server, input ->
                        input.require("from", "to", "state")
                        val changed =
                            server.fillRegion(
                                input.stringOrNull("dimension"),
                                input.decode("from"),
                                input.decode("to"),
                                input.string("state"),
                                input.boolean("no_updates"),
                                input.stringOrNull("replace"),
                            )
                        done("Changed $changed blocks")
                    },
                "break" to
                    writeAction { server, input ->
                        input.require("pos")
                        server.breakBlock(
                            input.stringOrNull("dimension"),
                            input.decode("pos"),
                            input.boolean("drop_items"),
                        )
                        done("Block broken")
                    },
                "merge_nbt" to
                    writeAction { server, input ->
                        input.require("pos", "snbt")
                        server.mergeBlockEntity(
                            input.stringOrNull("dimension"),
                            input.decode("pos"),
                            input.string("snbt"),
                        )
                        done("Block entity updated")
                    },
            ),
    )

val GET_REGION =
    ToolDefinition(
        name = "get_region",
        title = "Read region",
        description =
            "Reads a cuboid region, corners inclusive. 'summary' counts each block state. 'draw' " +
                "prints every y layer as a character grid, up to $MAX_DRAWN_VOLUME blocks.",
        input =
            input {
                optional("dimension", DIMENSION)
                required("from", BLOCK_POS)
                required("to", BLOCK_POS)
                optional("detail", enumSchema(listOf("summary", "draw"), default = "summary"))
                optional(
                    "loaded_only",
                    booleanSchema(
                        "Do not load chunks. Unloaded positions read as void air.",
                        default = false,
                    ),
                )
            },
        actions = mapOf("read" to readAction { server, input -> server.regionReply(input) }),
    )

private fun MinecraftServer.regionReply(input: ToolInput): Reply {
    val from = input.decode<BlockCoordinates>("from")
    val to = input.decode<BlockCoordinates>("to")
    val drawing = input.string("detail") == "draw"

    if (drawing && volume(from, to) > MAX_DRAWN_VOLUME) {
        invalidInput("draw supports up to $MAX_DRAWN_VOLUME blocks")
    }

    val region = readRegion(input.stringOrNull("dimension"), from, to, input.boolean("loaded_only"))
    if (!drawing) return summarizeRegion(region)
    if (!paletteFits(region)) invalidInput("Too many block states to draw")
    return drawRegion(region)
}

private fun volume(from: BlockCoordinates, to: BlockCoordinates): Long {
    fun side(first: Int, second: Int) = abs(first.toLong() - second) + 1
    return side(from.x, to.x) * side(from.y, to.y) * side(from.z, to.z)
}
