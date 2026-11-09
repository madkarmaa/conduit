package top.madkarma.conduit.service.structure

import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.mcp.*

val STRUCTURES =
    ToolDefinition(
        name = "structures",
        title = "Structures",
        description =
            "Structure templates, as structure blocks use them. list finds saved, " +
                "datapack and vanilla templates. save copies a region into a template. place " +
                "pastes one with its lowest corner at origin. copy pastes a region at origin " +
                "without saving it, also into target_dimension.",
        input =
            input {
                optional(
                    "id",
                    stringSchema("Template id for save and place, such as \"builds:house\"."),
                )
                optional("filter", stringSchema("Substring of the id, for list.", default = ""))
                optional("limit", LIMIT)
                optional("dimension", DIMENSION)
                optional("from", BLOCK_POS.described("Region corner for save and copy."))
                optional("to", BLOCK_POS.described("Opposite region corner for save and copy."))
                optional("origin", BLOCK_POS.described("Required for place and copy."))
                optional(
                    "target_dimension",
                    stringSchema("For copy: where to paste. Defaults to dimension."),
                )
                optional(
                    "rotation",
                    enumSchema(
                        listOf("none", "clockwise_90", "clockwise_180", "counterclockwise_90"),
                        default = "none",
                    ),
                )
                optional(
                    "mirror",
                    enumSchema(listOf("none", "left_right", "front_back"), default = "none"),
                )
                optional("include_entities", booleanSchema(default = true))
                optional(
                    "integrity",
                    numberSchema(
                        "Chance each block is placed.",
                        min = 0.0,
                        max = 1.0,
                        default = 1.0,
                    ),
                )
                optional(
                    "ignore_block",
                    stringSchema(
                        "For save: block left out, such as \"minecraft:structure_void\".",
                        default = "",
                    ),
                )
            },
        actions =
            mapOf(
                "list" to
                    AsyncAction(Access.READ) { input, _ ->
                        val matching = listStructures().filter { input.string("filter") in it }
                        val page = matching.take(input.int("limit"))
                        lines(
                            "${matching.size} matching templates",
                            page,
                            dataOf("total" to JsonPrimitive(matching.size), "ids" to encoded(page)),
                        )
                    },
                "save" to
                    AsyncAction(Access.WRITE) { input, _ ->
                        input.require("id", "from", "to")
                        saveStructure(
                            input.string("id"),
                            input.stringOrNull("dimension"),
                            input.decode("from"),
                            input.decode("to"),
                            input.boolean("include_entities"),
                            input.string("ignore_block"),
                        )
                        done("Saved ${input.string("id")}")
                    },
                "place" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        input.require("id", "origin")
                        val placement = placement(input, input.stringOrNull("dimension"))
                        val placed =
                            placeStoredStructure(input.string("id"), placement, input.policy.name)
                        json(placed.result).noteUndo(placed.undoable)
                    },
                "copy" to
                    destructiveAction { server, input ->
                        val target =
                            input.stringOrNull("target_dimension")
                                ?: input.stringOrNull("dimension")
                        val template =
                            server.captureStructure(
                                input.stringOrNull("dimension"),
                                input.decode("from"),
                                input.decode("to"),
                                input.boolean("include_entities"),
                                null,
                            )
                        json(server.placeStructure(template, placement(input, target)))
                    },
            ),
    )

private fun placement(input: ToolInput, dimension: String?) =
    StructurePlacement(
        dimension = dimension,
        origin = input.decode("origin"),
        rotation = StructureRotation.valueOf(input.string("rotation").uppercase()),
        mirror = StructureMirror.valueOf(input.string("mirror").uppercase()),
        includeEntities = input.boolean("include_entities"),
        integrity = input.float("integrity"),
    )
