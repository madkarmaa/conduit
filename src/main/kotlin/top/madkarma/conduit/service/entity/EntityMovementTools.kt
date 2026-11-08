package top.madkarma.conduit.service.entity

import top.madkarma.conduit.game.Rotation
import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*

val MOVE_ENTITY =
    ToolDefinition(
        name = "move_entity",
        title = "Move entity",
        description =
            "teleport moves an entity, players included, also across dimensions. rotate turns " +
                "it. push sets its velocity in blocks per tick. mount puts it on a vehicle, and " +
                "dismount takes it off. walk makes a mob walk to position along a path, though " +
                "its own goals can take over, and stop ends the walk.",
        input =
            input {
                required("uuid", UUID)
                optional("dimension", stringSchema("Target dimension for teleport."))
                optional("position", VEC3.described("Required for teleport and walk."))
                optional(
                    "speed",
                    numberSchema(
                        "walk speed, times the mob's own.",
                        min = 0.1,
                        max = 3.0,
                        default = 1.0,
                    ),
                )
                optional(
                    "rotation",
                    ROTATION.described("Required for rotate, optional for teleport."),
                )
                optional("velocity", VEC3.described("Required for push."))
                optional(
                    "add",
                    booleanSchema(
                        "push adds to the current velocity instead of replacing it.",
                        default = false,
                    ),
                )
                optional("vehicle", stringSchema("Vehicle UUID. Required for mount."))
            },
        actions =
            mapOf(
                "teleport" to
                    writeAction { server, input ->
                        input.require("position")
                        val moved =
                            server.teleportEntity(
                                input.string("uuid"),
                                input.stringOrNull("dimension"),
                                input.decode("position"),
                                input.decodeOrNull<Rotation>("rotation"),
                            )
                        done(describeEntity(moved))
                    },
                "rotate" to
                    writeAction { server, input ->
                        input.require("rotation")
                        server.changeRotation(input.string("uuid"), input.decode("rotation"))
                        done("Entity rotated")
                    },
                "push" to
                    writeAction { server, input ->
                        input.require("velocity")
                        server.changeVelocity(
                            input.string("uuid"),
                            input.decode("velocity"),
                            input.boolean("add"),
                        )
                        done("Velocity set")
                    },
                "mount" to
                    writeAction { server, input ->
                        input.require("vehicle")
                        server.mount(input.string("uuid"), input.string("vehicle"))
                        done("Mounted")
                    },
                "dismount" to
                    writeAction { server, input ->
                        server.dismount(input.string("uuid"))
                        done("Dismounted")
                    },
                "walk" to
                    writeAction { server, input ->
                        val target = input.decode<Vector3>("position")
                        server.walkTo(input.string("uuid"), target, input.double("speed"))
                        done("Walking to ${position(target)}")
                    },
                "stop" to
                    writeAction { server, input ->
                        server.stopWalking(input.string("uuid"))
                        done("Stopped")
                    },
            ),
    )
