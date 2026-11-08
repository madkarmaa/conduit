package top.madkarma.conduit.service.world

import kotlin.math.abs
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.player.containerContents
import top.madkarma.conduit.service.player.describeInventory

// Drawn regions print one character per block, so they stay small enough to read.
private const val MAX_DRAWN_VOLUME = 8192

val GET_BLOCKS =
    ToolDefinition(
        name = "get_blocks",
        title = "Read blocks",
        description =
            "block reads the block state, biome and block entity SNBT (chest, sign, ...) at pos. " +
                "container lists the items of the chest, barrel, furnace or other container at " +
                "pos; a double chest counts as one container. " +
                "region reads a cuboid, corners inclusive: detail summary counts each block " +
                "state, and draw prints every y layer as a character grid, up to " +
                "$MAX_DRAWN_VOLUME blocks.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional("pos", BLOCK_POS.described("Required for block and container."))
                optional("from", BLOCK_POS.described("Region corner. Required for region."))
                optional("to", BLOCK_POS.described("Opposite region corner. Required for region."))
                optional("detail", enumSchema(listOf("summary", "draw"), default = "summary"))
                optional(
                    "loaded_only",
                    booleanSchema(
                        "Do not load chunks. Unloaded positions read as void air.",
                        default = false,
                    ),
                )
            },
        actions =
            mapOf(
                "block" to
                    readAction { server, input ->
                        json(
                            server.describeBlock(
                                input.stringOrNull("dimension"),
                                input.decode("pos"),
                            ),
                        )
                    },
                "region" to readAction { server, input -> server.regionReply(input) },
                "container" to
                    readAction { server, input ->
                        val pos = input.decode<BlockCoordinates>("pos")
                        describeInventory(
                            "Non-empty slots of the container at ${pos.x} ${pos.y} ${pos.z}:",
                            server.containerContents(input.stringOrNull("dimension"), pos),
                        )
                    },
            ),
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
