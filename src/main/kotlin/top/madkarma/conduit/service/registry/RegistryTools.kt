package top.madkarma.conduit.service.registry

import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.mcp.*

private val OFFSET = integerSchema(min = 0, default = 0)

val REGISTRY =
    ToolDefinition(
        name = "registry",
        title = "Game registries",
        description =
            "Looks up valid ids, including datapack content. registries lists every registry, " +
                "such as \"minecraft:block\", \"minecraft:item\", \"minecraft:entity_type\" and " +
                "\"minecraft:worldgen/biome\". entries lists the ids in one. tags lists its tags.",
        input =
            input {
                optional("registry", stringSchema("Required for entries and tags."))
                optional(
                    "filter",
                    stringSchema("Case-insensitive substring of the id.", default = ""),
                )
                optional(
                    "tag",
                    stringSchema("For entries: only members of this tag, e.g. \"minecraft:logs\"."),
                )
                optional("offset", OFFSET)
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "registries" to
                    readAction { server, input ->
                        val (total, items) = page(server.describeRegistries(), input) { it.id }
                        lines(
                            "$total matching registries",
                            items.map { "${it.id} (${it.entryCount})" },
                            dataOf("total" to JsonPrimitive(total), "registries" to encoded(items)),
                        )
                    },
                "entries" to
                    readAction { server, input ->
                        input.require("registry")
                        val entries =
                            server.readRegistryEntries(
                                input.string("registry"),
                                input.string("filter"),
                                input.stringOrNull("tag"),
                                input.int("offset"),
                                input.int("limit"),
                            )
                        lines(
                            "${entries.total} matching entries",
                            entries.ids,
                            encodedObject(entries),
                        )
                    },
                "tags" to
                    readAction { server, input ->
                        input.require("registry")
                        val (total, items) =
                            page(server.readTags(input.string("registry")), input) { it.id }
                        lines(
                            "$total matching tags",
                            items.map { "#${it.id}: ${it.entries.joinToString(" ")}" },
                            dataOf("total" to JsonPrimitive(total), "tags" to encoded(items)),
                        )
                    },
            ),
    )

val RECIPES =
    ToolDefinition(
        name = "recipes",
        title = "Recipes and loot tables",
        description =
            "list finds recipe ids, optionally by type or result item. get reads a recipe and " +
                "loot_table a loot table, such as \"minecraft:blocks/diamond_ore\", as datapack JSON.",
        input =
            input {
                optional(
                    "id",
                    stringSchema(
                        "Recipe or loot table id for get and loot_table, e.g. \"minecraft:crafting_table\".",
                    ),
                )
                optional("type", stringSchema("e.g. \"minecraft:crafting_shaped\".", default = ""))
                optional("result_item", stringSchema("e.g. \"minecraft:torch\".", default = ""))
                optional("offset", OFFSET)
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "list" to
                    readAction { server, input ->
                        val recipes =
                            server.readRecipes(
                                input.string("type"),
                                input.string("result_item"),
                                input.int("offset"),
                                input.int("limit"),
                            )
                        lines(
                            "${recipes.total} matching recipes",
                            recipes.recipes.map { "${it.id} (${it.type})" },
                            dataOf(
                                "total" to JsonPrimitive(recipes.total),
                                "ids" to encoded(recipes.recipes.map { it.id }),
                            ),
                        )
                    },
                "get" to
                    readAction { server, input ->
                        input.require("id")
                        done(server.readRecipe(input.string("id")).json)
                    },
                "loot_table" to
                    readAction { server, input ->
                        input.require("id")
                        done(server.readLootTable(input.string("id")))
                    },
            ),
    )

// Registries and tags come back whole, so this filters and pages them like the entries.
private fun <Item> page(
    all: List<Item>,
    input: ToolInput,
    id: (Item) -> String,
): Pair<Int, List<Item>> {
    val filter = input.string("filter")
    val matching = all.filter { id(it).contains(filter, ignoreCase = true) }
    return matching.size to matching.drop(input.int("offset")).take(input.int("limit"))
}
