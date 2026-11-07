package top.madkarma.conduit.service.entity

import top.madkarma.conduit.game.EffectInfo
import top.madkarma.conduit.mcp.*

val EDIT_ENTITY =
    ToolDefinition(
        name = "edit_entity",
        title = "Edit entity",
        description =
            "name sets or clears the custom name. tags adds and removes scoreboard tags. health " +
                "sets the health. damage hurts the entity like an attack. nbt merges SNBT into its " +
                "data. add_effect and remove_effect change status effects. set_attribute sets an " +
                "attribute base value such as \"minecraft:max_health\".",
        input =
            input {
                required("uuid", UUID)
                optional("name", TEXT.described("New custom name. Leave it out to clear it."))
                optional("name_visible", booleanSchema(default = false))
                optional("add_tags", arraySchema(stringSchema()))
                optional("remove_tags", arraySchema(stringSchema()))
                optional("amount", numberSchema("Health or damage amount.", min = 0.0))
                optional("damage_type", stringSchema(default = "minecraft:generic"))
                optional("attacker", stringSchema("UUID of the attacking entity."))
                optional("snbt", SNBT.described("Required for nbt."))
                optional(
                    "effect",
                    stringSchema(
                        "Effect id, e.g. \"minecraft:speed\"; remove_effect without it clears all.",
                    ),
                )
                optional(
                    "duration_ticks",
                    integerSchema("-1 means infinite.", min = -1, default = 600),
                )
                optional(
                    "amplifier",
                    integerSchema("0 is level I.", min = 0, max = 255, default = 0),
                )
                optional("show_particles", booleanSchema(default = true))
                optional("attribute", stringSchema("Required for set_attribute."))
                optional("base", numberSchema("Required for set_attribute."))
            },
        actions =
            mapOf(
                "name" to
                    writeAction { server, input ->
                        val name = input.stringOrNull("name")
                        server.changeCustomName(
                            input.string("uuid"),
                            name,
                            input.boolean("name_visible"),
                        )
                        done(if (name.isNullOrEmpty()) "Name cleared" else "Name set")
                    },
                "tags" to
                    writeAction { server, input ->
                        server.changeTags(
                            input.string("uuid"),
                            input.strings("add_tags"),
                            input.strings("remove_tags"),
                        )
                        done("Tags updated")
                    },
                "health" to
                    writeAction { server, input ->
                        input.require("amount")
                        server.changeHealth(input.string("uuid"), input.float("amount"))
                        done("Health set to ${round(input.double("amount"))}")
                    },
                "damage" to
                    writeAction { server, input ->
                        input.require("amount")
                        server.damage(
                            input.string("uuid"),
                            input.float("amount"),
                            input.string("damage_type"),
                            input.stringOrNull("attacker"),
                        )
                        done("Dealt ${round(input.double("amount"))} damage")
                    },
                "nbt" to
                    writeAction { server, input ->
                        input.require("snbt")
                        server.mergeEntityNbt(input.string("uuid"), input.string("snbt"))
                        done("Entity data merged")
                    },
                "add_effect" to
                    writeAction { server, input ->
                        input.require("effect")
                        val effect =
                            EffectInfo(
                                id = input.string("effect"),
                                duration = input.int("duration_ticks"),
                                amplifier = input.int("amplifier"),
                                ambient = false,
                                visible = input.boolean("show_particles"),
                                showIcon = true,
                            )
                        server.addEffect(input.string("uuid"), effect)
                        done("Added ${effect.id}")
                    },
                "remove_effect" to
                    writeAction { server, input ->
                        server.removeEffect(input.string("uuid"), input.stringOrNull("effect"))
                        done("Effects removed")
                    },
                "set_attribute" to
                    writeAction { server, input ->
                        input.require("attribute", "base")
                        val attribute = input.string("attribute")
                        server.changeAttributeBase(
                            input.string("uuid"),
                            attribute,
                            input.double("base"),
                        )
                        done("$attribute base set to ${round(input.double("base"))}")
                    },
            ),
    )
