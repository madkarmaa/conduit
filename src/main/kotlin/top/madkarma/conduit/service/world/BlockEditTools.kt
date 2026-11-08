package top.madkarma.conduit.service.world

import net.minecraft.core.Direction
import top.madkarma.conduit.mcp.*

val EDIT_BLOCKS =
    ToolDefinition(
        name = "edit_blocks",
        title = "Edit blocks",
        description =
            "set places block states at positions in one tick. fill fills a cuboid, corners " +
                "inclusive. shape fills a sphere or a cylinder around center, a box, its four " +
                "walls or a line from one corner to the other. hollow keeps only the outer " +
                "layer. break breaks a block like a player. merge_nbt merges SNBT into a block " +
                "entity.",
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
                optional("from", BLOCK_POS.described("Corner for fill and shape."))
                optional("to", BLOCK_POS.described("Opposite corner for fill and shape."))
                optional("state", BLOCK_STATE.described("Required for fill and shape."))
                optional(
                    "replace",
                    stringSchema(
                        "fill and shape only replace blocks matching this block id, state or #tag.",
                    ),
                )
                shapeFields()
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
                "shape" to
                    destructiveAction { server, input ->
                        val changed =
                            server.fillShape(
                                input.stringOrNull("dimension"),
                                shapeRequest(input),
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

private fun InputBuilder.shapeFields() {
    optional(
        "shape",
        enumSchema(listOf("sphere", "cylinder", "box", "walls", "line"), "Required for shape."),
    )
    optional("center", BLOCK_POS.described("Sphere center, or the middle of a cylinder's base."))
    optional("radius", integerSchema("For sphere and cylinder.", min = 0, default = 0))
    optional(
        "height",
        integerSchema("Cylinder length along its axis, from center.", min = 1, default = 1),
    )
    optional("axis", enumSchema(listOf("x", "y", "z"), "Cylinder axis.", default = "y"))
    optional("hollow", booleanSchema("shape keeps only the outer layer.", default = false))
}

private fun shapeRequest(input: ToolInput) =
    ShapeRequest(
        shape = Shape.valueOf(input.string("shape").uppercase()),
        center = input.decodeOrNull("center"),
        radius = input.int("radius"),
        height = input.int("height"),
        axis = Direction.Axis.valueOf(input.string("axis").uppercase()),
        from = input.decodeOrNull("from"),
        to = input.decodeOrNull("to"),
        hollow = input.boolean("hollow"),
    )
