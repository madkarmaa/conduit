package top.madkarma.conduit.service.world

import kotlin.math.abs
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.inventory.containerContents
import top.madkarma.conduit.service.inventory.describeInventory

// Drawn regions print one character per block, so they stay small enough to read.
private const val MAX_DRAWN_VOLUME = 8192

val GET_BLOCKS =
    ToolDefinition(
        name = "get_blocks",
        title = "Read blocks",
        description =
            "block reads a block's state, biome and block entity SNBT. container lists a " +
                "container's items. region reads a cuboid: summary counts the states, draw " +
                "prints each y layer as a grid of up to 8192 blocks, and image returns a " +
                "top-view PNG of up to 256 by 256 columns. watch lists the changes at up to " +
                "64 positions every tick for up to 200 ticks, such as to test redstone.",
        input =
            input {
                optional("dimension", DIMENSION)
                optional("pos", BLOCK_POS.described("Required for block and container."))
                optional("from", BLOCK_POS.described("Region corner. Required for region."))
                optional("to", BLOCK_POS.described("Opposite region corner. Required for region."))
                optional(
                    "positions",
                    arraySchema(
                        BLOCK_POS,
                        "Required for watch.",
                        minItems = 1,
                        maxItems = MAX_WATCHED_BLOCKS,
                    ),
                )
                optional(
                    "ticks",
                    integerSchema("For watch.", min = 1, max = MAX_WATCH_TICKS, default = 40),
                )
                optional(
                    "detail",
                    enumSchema(listOf("summary", "draw", "image"), default = "summary"),
                )
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
                "watch" to
                    AsyncAction(Access.READ) { input, _ ->
                        watchBlocks(
                            input.stringOrNull("dimension"),
                            input.decode("positions"),
                            input.int("ticks"),
                        )
                    },
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
    if (input.string("detail") == "image") {
        val image =
            drawRegionImage(input.stringOrNull("dimension"), from, to, input.boolean("loaded_only"))
        return imageReply(image, from, to)
    }

    if (drawing) checkDrawable(from, to)

    val region = readRegion(input.stringOrNull("dimension"), from, to, input.boolean("loaded_only"))
    if (!drawing) return summarizeRegion(region)
    if (!paletteFits(region)) {
        invalidInput(
            "The region has ${region.palette.size} block states, too many to draw. Draw fewer " +
                "layers at a time, or read it with summary.",
        )
    }
    return drawRegion(region)
}

private fun checkDrawable(from: BlockCoordinates, to: BlockCoordinates) {
    fun side(first: Int, second: Int) = abs(first.toLong() - second) + 1
    val sizeX = side(from.x, to.x)
    val sizeY = side(from.y, to.y)
    val sizeZ = side(from.z, to.z)
    if (sizeX * sizeY * sizeZ > MAX_DRAWN_VOLUME) {
        invalidInput(tooLarge(sizeX, sizeY, sizeZ, MAX_DRAWN_VOLUME.toLong()))
    }
}

private fun imageReply(image: RegionImage, from: BlockCoordinates, to: BlockCoordinates): Reply {
    val legend = image.legend.map { (id, color) -> "$id #%06X".format(color and 0xFFFFFF) }
    val header =
        "Top view from ${from.x} ${from.z} to ${to.x} ${to.z}, ${image.scale} pixels per block, " +
            "north up. Most common surface blocks:"
    return Reply((listOf(header) + legend).joinToString("\n"), image = image.pixels)
}
