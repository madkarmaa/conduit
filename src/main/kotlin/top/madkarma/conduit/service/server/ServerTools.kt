package top.madkarma.conduit.service.server

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.GAME_JSON
import top.madkarma.conduit.game.Rotation
import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.world.describeDimensions

val SERVER_INFO =
    ToolDefinition(
        name = "server_info",
        title = "Server info",
        description =
            "summary reads the versions, players, seed, difficulty, TPS, MSPT, memory and " +
                "dimensions. mods and backups list those.",
        input = input {},
        defaultAction = "summary",
        actions =
            mapOf(
                "summary" to readAction { server, _ -> json(server.summary()) },
                "mods" to
                    readAction { server, _ ->
                        val mods = server.describeServer().mods
                        lines(
                            "${mods.size} mods and libraries",
                            mods,
                            dataOf("mods" to encoded(mods)),
                        )
                    },
                "backups" to
                    AsyncAction(Access.READ) { _, _ -> describeBackupList(describeBackups()) },
            ),
    )

val RUN_COMMAND =
    ToolDefinition(
        name = "run_command",
        title = "Run command",
        description =
            "Runs a command like the console and returns its success, result value and " +
                "feedback. It can run as a player or entity, at a position.",
        input =
            input {
                required(
                    "command",
                    stringSchema("Command with or without the slash, such as \"time set day\"."),
                )
                optional("as", stringSchema("Player name or UUID, or entity UUID to run as."))
                optional("dimension", stringSchema())
                optional("position", VEC3)
                optional("rotation", ROTATION)
                optional("permission_level", integerSchema(min = 0, max = 4, default = 4))
            },
        actions =
            mapOf(
                "run" to
                    destructiveAction { server, input ->
                        val request =
                            CommandRequest(
                                command = input.string("command"),
                                runAs = input.stringOrNull("as"),
                                dimension = input.stringOrNull("dimension"),
                                position = input.decodeOrNull<Vector3>("position"),
                                rotation = input.decodeOrNull<Rotation>("rotation"),
                                permissionLevel = input.int("permission_level"),
                            )
                        val result = server.executeCommand(request)
                        val status = if (result.success) "Succeeded" else "Failed"

                        lines(
                            "$status, result ${result.result}",
                            result.output,
                            encodedObject(result),
                        )
                    },
            ),
    )

val SERVER_ADMIN =
    ToolDefinition(
        name = "server_admin",
        title = "Server administration",
        description =
            "save writes worlds and players to disk. backup zips the world while the " +
                "server runs. stop saves and stops the server.",
        input =
            input {
                optional(
                    "flush",
                    booleanSchema("save waits until everything is written.", default = false),
                )
                optional("name", stringSchema("Appended to the backup file name.", maxLength = 64))
            },
        actions =
            mapOf(
                "save" to
                    writeAction { server, input ->
                        server.saveAll(input.boolean("flush"))
                        done("Saved")
                    },
                "backup" to
                    AsyncAction(Access.WRITE) { input, _ ->
                        json(createBackup(input.stringOrNull("name")))
                    },
                "stop" to
                    destructiveAction { server, _ ->
                        server.scheduleStop()
                        done("Server stopping")
                    },
            ),
    )

// The mods action lists the mods, which would fill most of the summary.
private fun MinecraftServer.summary(): JsonObject {
    val fields = GAME_JSON.encodeToJsonElement(describeServer()).jsonObject
    return JsonObject(
        fields - "mods" +
            ("dimensions" to GAME_JSON.encodeToJsonElement(describeDimensions())) +
            ("performance" to GAME_JSON.encodeToJsonElement(performance())),
    )
}

private fun describeBackupList(backups: List<BackupFile>): Reply =
    lines(
        "${backups.size} backups",
        backups.map {
            "${it.name} ${it.sizeBytes} bytes, created ${Instant.ofEpochMilli(it.createdTimestampMs)}"
        },
        dataOf("backups" to encoded(backups)),
    )
