package top.madkarma.conduit.service.entity

import top.madkarma.conduit.game.Rotation
import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*

val MOVE_ENTITY =
    ToolDefinition(
        name = "move_entity",
        title = "Move entity",
        description =
            "teleport moves any entity, also across dimensions, and names the block it " +
                "lands on. rotate turns it, push sets its velocity, and mount and dismount " +
                "change what it rides. walk makes a mob walk to position, or follow target " +
                "while it moves. A walking mob does not despawn, and the walk ends on " +
                "arrival, after 2 minutes or with stop.",
        input =
            input {
                required("uuid", UUID)
                optional("dimension", stringSchema("Target dimension for teleport."))
                optional(
                    "position",
                    VEC3.described("Required for teleport. For walk, unless target is set."),
                )
                optional(
                    "target",
                    stringSchema("For walk: a player name or entity UUID to walk to and follow."),
                )
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
                        val landing =
                            server.teleportEntity(
                                input.string("uuid"),
                                input.stringOrNull("dimension"),
                                input.decode("position"),
                                input.decodeOrNull<Rotation>("rotation"),
                            )
                        val stuck = if (landing.free) "" else ", stuck inside blocks"
                        done(
                            "${describeEntity(landing.entity)}, standing on " +
                                "${landing.blockBelow}$stuck",
                        )
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
                        val goal =
                            WalkGoal(
                                input.decodeOrNull<Vector3>("position"),
                                input.stringOrNull("target"),
                            )
                        val walk = server.walkTo(input.string("uuid"), goal, input.double("speed"))
                        val name = walk.targetName?.let { "following $it at " }.orEmpty()
                        done(
                            "Walking, ${name}${position(walk.goal)}, ${round(walk.distance)} blocks away",
                        )
                    },
                "stop" to
                    writeAction { server, input ->
                        server.stopWalking(input.string("uuid"))
                        done("Stopped")
                    },
            ),
    )
