package top.madkarma.conduit.service.entity

import top.madkarma.conduit.game.EntityInfo
import top.madkarma.conduit.game.Rotation
import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*

val LIST_ENTITIES =
    ToolDefinition(
        name = "list_entities",
        title = "List entities",
        description =
            "Finds entities by type, area, tag or a vanilla selector. Each line lists one entity " +
                "with its uuid, type, name, dimension, position and health.",
        seesOtherPlayers = true,
        input =
            input {
                optional("dimension", stringSchema("Empty means every dimension."))
                optional("types", arraySchema(stringSchema(), "e.g. [\"minecraft:zombie\"]."))
                optional("center", VEC3.described("Requires dimension and radius."))
                optional("radius", numberSchema(min = 0.0))
                optional(
                    "box",
                    objectSchema {
                        required("min", VEC3)
                        required("max", VEC3)
                    },
                )
                optional("tag", stringSchema("Required scoreboard tag."))
                optional(
                    "selector",
                    stringSchema(
                        "Selector such as \"@e[type=cow,distance=..10]\"; overrides other filters.",
                    ),
                )
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "list" to
                    readAction { server, input ->
                        val query =
                            EntityQuery(
                                dimension = input.stringOrNull("dimension")?.ifBlank { null },
                                types = input.strings("types"),
                                box = input.decodeOrNull<Box>("box"),
                                center = input.decodeOrNull<Vector3>("center"),
                                radius = input.doubleOrNull("radius"),
                                selector = input.stringOrNull("selector"),
                                tag = input.stringOrNull("tag"),
                                limit = input.int("limit"),
                            )
                        val entities = server.findEntities(query)
                        lines(
                            "${entities.size} entities",
                            entities.map(::describeEntity),
                            dataOf("entities" to encoded(entities)),
                        )
                    },
            ),
    )

val GET_ENTITY =
    ToolDefinition(
        name = "get_entity",
        title = "Get entity",
        description =
            "info: full state of one entity. nbt: its complete data as SNBT. effects: its status " +
                "effects. attributes: its attributes with base and current values.",
        seesOtherPlayers = true,
        input = input { required("uuid", UUID) },
        defaultAction = "info",
        actions =
            mapOf(
                "info" to
                    readAction { server, input ->
                        json(server.describeEntity(input.string("uuid")))
                    },
                "nbt" to
                    readAction { server, input -> done(server.entityNbt(input.string("uuid"))) },
                "effects" to
                    readAction { server, input -> json(server.effectsOf(input.string("uuid"))) },
                "attributes" to
                    readAction { server, input -> json(server.attributesOf(input.string("uuid"))) },
            ),
    )

val SPAWN_ENTITY =
    ToolDefinition(
        name = "spawn_entity",
        title = "Spawn entity",
        description =
            "Spawns an entity in a loaded chunk and returns it. The force_chunk action of " +
                "world_settings loads chunks away from players.",
        input =
            input {
                required("type", stringSchema("Entity type, e.g. \"minecraft:villager\"."))
                optional("dimension", DIMENSION)
                required("position", VEC3)
                optional("rotation", ROTATION)
                optional(
                    "snbt",
                    SNBT.described("Data applied on spawn, like the /summon nbt argument."),
                )
                optional(
                    "initialize",
                    booleanSchema("Run natural spawn setup (equipment, variants).", default = true),
                )
            },
        actions =
            mapOf(
                "spawn" to
                    writeAction { server, input ->
                        val request =
                            SpawnRequest(
                                type = input.string("type"),
                                dimension = input.stringOrNull("dimension"),
                                position = input.decode("position"),
                                rotation = input.decodeOrNull<Rotation>("rotation"),
                                snbt = input.stringOrNull("snbt"),
                                initialize = input.boolean("initialize"),
                            )
                        json(server.spawnEntity(request))
                    },
            ),
    )

val REMOVE_ENTITY =
    ToolDefinition(
        name = "remove_entity",
        title = "Remove entity",
        description = "Removes an entity silently, or kills it so it drops loot.",
        input =
            input {
                required("uuid", UUID)
                optional("kill", booleanSchema(default = false))
            },
        actions =
            mapOf(
                "remove" to
                    destructiveAction { server, input ->
                        server.removeEntity(input.string("uuid"), input.boolean("kill"))
                        done("Entity removed")
                    },
            ),
    )

/** One line with the entity's uuid, type, name, dimension, position and health. */
fun describeEntity(entity: EntityInfo): String {
    val health =
        entity.health?.let {
            " ${round(it.toDouble())}/${round((entity.maxHealth ?: it).toDouble())}hp"
        }
    val name = entity.customName?.takeIf { it.isNotEmpty() }?.let { " \"$it\"" }
    return "${entity.uuid} ${entity.type}${name.orEmpty()} ${entity.dimension} ${position(entity.position)}${health.orEmpty()}"
}
