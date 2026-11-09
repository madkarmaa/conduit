package top.madkarma.conduit.service.registry

import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.mcp.*

private val OFFSET = integerSchema(min = 0, default = 0)

val GAME_DATA =
    ToolDefinition(
        name = "game_data",
        title = "Game data",
        description =
            "Looks up ids and content, datapacks included. registries lists the " +
                "registries, entries the ids in one, and tags its tags. recipes finds recipes " +
                "by type or result. recipe and loot_table return datapack JSON.",
        input =
            input {
                optional("registry", stringSchema("Required for entries and tags."))
                optional(
                    "filter",
                    stringSchema(
                        "Substring of the id, any case. Advancements also match their title.",
                        default = "",
                    ),
                )
                optional(
                    "tag",
                    stringSchema(
                        "For entries: only members of this tag, such as \"minecraft:logs\".",
                    ),
                )
                optional(
                    "id",
                    stringSchema(
                        "Recipe or loot table id. Required for recipe and loot_table.",
                    ),
                )
                optional(
                    "type",
                    stringSchema(
                        "Recipe type for recipes, such as \"minecraft:crafting_shaped\".",
                        default = "",
                    ),
                )
                optional(
                    "result_item",
                    stringSchema(
                        "Result item for recipes, such as \"minecraft:torch\".",
                        default = "",
                    ),
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
                "entries" to readAction { server, input -> server.entriesReply(input) },
                "tags" to
                    readAction { server, input ->
                        val (total, items) =
                            page(server.readTags(input.string("registry")), input) { it.id }
                        lines(
                            "$total matching tags",
                            items.map { "#${it.id}: ${it.entries.joinToString(" ")}" },
                            dataOf("total" to JsonPrimitive(total), "tags" to encoded(items)),
                        )
                    },
                "recipes" to readAction { server, input -> server.recipesReply(input) },
                "recipe" to
                    readAction { server, input ->
                        done(server.readRecipe(input.string("id")).json)
                    },
                "loot_table" to
                    readAction { server, input -> done(server.readLootTable(input.string("id"))) },
            ),
    )

private fun MinecraftServer.entriesReply(input: ToolInput): Reply {
    val entries =
        readRegistryEntries(
            input.string("registry"),
            input.string("filter"),
            input.stringOrNull("tag"),
            input.int("offset"),
            input.int("limit"),
        )
    val rows = entries.ids.map { id -> entries.titles[id]?.let { "$id \"$it\"" } ?: id }
    return lines("${entries.total} matching entries", rows, encodedObject(entries))
}

private fun MinecraftServer.recipesReply(input: ToolInput): Reply {
    val recipes =
        readRecipes(
            input.string("type"),
            input.string("result_item"),
            input.int("offset"),
            input.int("limit"),
        )
    return lines(
        "${recipes.total} matching recipes",
        recipes.recipes.map { "${it.id} (${it.type})" },
        dataOf(
            "total" to JsonPrimitive(recipes.total),
            "ids" to encoded(recipes.recipes.map { it.id }),
        ),
    )
}

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
