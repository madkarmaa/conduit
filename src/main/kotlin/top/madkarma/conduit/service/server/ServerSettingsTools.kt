package top.madkarma.conduit.service.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*

// Agents send numbers and flags as JSON numbers and booleans as often as text.
private val VALUE =
    JsonObject(
        mapOf(
            "type" to JsonArray(listOf("string", "integer", "boolean").map(::JsonPrimitive)),
            "description" to JsonPrimitive("New value. Required for set and set_access."),
        ),
    )

val SERVER_SETTINGS =
    ToolDefinition(
        name = "server_settings",
        title = "Server settings",
        description =
            "get reads the server.properties settings that apply while the server runs. set " +
                "changes one at once and saves it. set_access changes the ones that decide who " +
                "may join or act. Other settings, such as build height, need a restart.",
        input =
            input {
                optional(
                    "setting",
                    enumSchema(
                        LIVE_SETTINGS.map { it.name },
                        "get reads every setting without it.",
                    ),
                )
                optional("value", VALUE)
            },
        defaultAction = "get",
        actions =
            mapOf(
                "get" to readAction { server, input -> server.settingsReply(input) },
                "set" to writeAction { server, input -> server.changeReply(input, access = false) },
                "set_access" to
                    destructiveAction { server, input -> server.changeReply(input, access = true) },
            ),
    )

private fun MinecraftServer.settingsReply(input: ToolInput): Reply {
    val server = dedicated()
    val chosen = input.stringOrNull("setting")?.let { listOf(setting(it)) } ?: LIVE_SETTINGS
    return done(chosen.joinToString("\n") { "${it.name} ${it.read(server)}" })
}

private fun MinecraftServer.changeReply(input: ToolInput, access: Boolean): Reply {
    input.require("setting", "value")
    val server = dedicated()
    val setting = setting(input.string("setting"))
    if (setting.access != access) {
        val action = if (setting.access) "set_access" else "set"
        invalidInput("${setting.name} is changed with $action")
    }

    val before = setting.read(server)
    setting.write(server, input.string("value"))
    return done("${setting.name} changed from $before to ${setting.read(server)}")
}
