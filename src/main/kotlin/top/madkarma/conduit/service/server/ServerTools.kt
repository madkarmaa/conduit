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
            "summary reads the Minecraft and mod versions, players, seed, difficulty, " +
                "performance (TPS, MSPT, memory) and the dimensions with their height range. " +
                "mods, datapacks and backups list those.",
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
                "datapacks" to readAction { server, _ -> json(server.listDatapacks()) },
                "backups" to
                    AsyncAction(Access.READ) { _, _ -> describeBackupList(describeBackups()) },
            ),
    )

val RUN_COMMAND =
    ToolDefinition(
        name = "run_command",
        title = "Run command",
        description =
            "Runs any command like the server console, and returns whether it succeeded, its " +
                "result value and its feedback. Can run as a player or entity and at a position.",
        input =
            input {
                required(
                    "command",
                    stringSchema("Command with or without the slash, e.g. \"time set day\"."),
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
            "save writes worlds and players to disk. backup saves the world and zips it while " +
                "the server keeps running. set_datapack enables or disables a datapack and " +
                "reloads the data. stop saves and stops the server.",
        input =
            input {
                optional(
                    "flush",
                    booleanSchema("save waits until everything is written.", default = false),
                )
                optional("name", stringSchema("Appended to the backup file name.", maxLength = 64))
                optional("id", stringSchema("Datapack id for set_datapack, e.g. \"file/my_pack\"."))
                optional("enabled", booleanSchema("Required for set_datapack."))
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
                "set_datapack" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        input.require("id", "enabled")
                        json(changeDatapackEnabled(input.string("id"), input.boolean("enabled")))
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
