package top.madkarma.conduit.service.entity

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.EntityInfo
import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*

val ENTITIES =
    ToolDefinition(
        name = "entities",
        title = "Entities",
        description =
            "list finds entities by type, area, tag or a vanilla selector, one line each with " +
                "the uuid, type, name, dimension, position and health. info reads the full state " +
                "of one entity, nbt its complete data as SNBT, effects its status effects, and " +
                "attributes its attributes with base and current values. Callers limited to " +
                "one player's data see no other players.",
        input =
            input {
                optional("uuid", UUID.described("Required for info, nbt, effects and attributes."))
                optional("dimension", stringSchema("For list. Empty means every dimension."))
                optional(
                    "types",
                    arraySchema(stringSchema(), "Entity types, such as [\"minecraft:zombie\"]."),
                )
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
                "list" to readAction { server, input -> server.listEntities(input) },
                "info" to
                    readAction { server, input ->
                        json(server.describeEntity(server.visibleUuid(input)))
                    },
                "nbt" to
                    readAction { server, input ->
                        done(server.entityNbt(server.visibleUuid(input)))
                    },
                "effects" to
                    readAction { server, input ->
                        json(server.effectsOf(server.visibleUuid(input)))
                    },
                "attributes" to
                    readAction { server, input ->
                        json(server.attributesOf(server.visibleUuid(input)))
                    },
            ),
    )

private fun MinecraftServer.listEntities(input: ToolInput): Reply {
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
            onlyPlayer = input.policy.player?.uuid,
        )
    val entities = findEntities(query)
    return lines(
        "${entities.size} entities",
        entities.map(::describeEntity),
        dataOf("entities" to encoded(entities)),
    )
}

private fun MinecraftServer.visibleUuid(input: ToolInput): String {
    val uuid = input.string("uuid")
    visibleEntity(uuid, input.policy.player?.uuid)
    return uuid
}

/** One line with the entity's uuid, type, name, dimension, position and health. */
fun describeEntity(entity: EntityInfo): String {
    val health =
        entity.health?.let {
            " ${round(it.toDouble())}/${round((entity.maxHealth ?: it).toDouble())}hp"
        }
    val name = entity.customName?.takeIf { it.isNotEmpty() }?.let { " \"$it\"" }
    return "${entity.uuid} ${entity.type}${name.orEmpty()} ${entity.dimension} ${position(entity.position)}${health.orEmpty()}"
}
