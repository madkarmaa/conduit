package top.madkarma.conduit.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.invalidInput

private const val MAX_CALLS = 1000

private class BatchOutcome(call: PreparedCall, val text: String, val status: String) {
    val label = "${call.tool.name}.${call.actionName}"
}

val BATCH =
    ToolDefinition(
        name = "batch",
        title = "Batch",
        description =
            "Runs calls to other tools in order within one server tick, so no other game logic " +
                "runs between them. Each call names a tool and its arguments, as a direct call " +
                "would pass them. The batch checks every call before the first one runs. It is " +
                "not atomic, so the calls before a failed one stay applied. Actions that wait or " +
                "work with files, such as backups, datapack changes, structures and waiting for " +
                "events, cannot run in a batch.",
        input =
            input {
                required(
                    "calls",
                    arraySchema(
                        objectSchema {
                            required("tool", stringSchema("Tool name, such as \"edit_blocks\"."))
                            optional(
                                "arguments",
                                objectSchema(
                                    "The tool's arguments, such as {\"action\": \"set\", \"changes\": [...]}.",
                                ) {},
                            )
                        },
                        minItems = 1,
                        maxItems = MAX_CALLS,
                    ),
                )
                optional(
                    "stop_on_error",
                    booleanSchema("Skip the rest after a failure.", default = false),
                )
            },
        approvesItself = true,
        actions =
            mapOf(
                "run" to
                    AsyncAction(Access.DESTRUCTIVE) { input, session -> runBatch(input, session) },
            ),
    )

private suspend fun runBatch(input: ToolInput, session: ToolSession): Reply {
    val calls =
        (input.arguments.getValue("calls") as JsonArray).mapIndexed { index, call ->
            prepare(index, call.jsonObject, session)
        }

    session.approve(calls.map { it.called() })

    val stopOnError = input.boolean("stop_on_error")
    val outcomes = GameServer.run { server ->
        runInOneTick(server, calls, stopOnError, session.policy)
    }

    val rows = outcomes.mapIndexed { index, outcome ->
        val separator = if (outcome.status == "ok") ":" else " ${outcome.status}:"
        "$index ${outcome.label}$separator ${outcome.text}".trimEnd()
    }

    val results = outcomes.mapIndexed { index, outcome ->
        dataOf(
            "index" to JsonPrimitive(index),
            "call" to JsonPrimitive(outcome.label),
            "status" to JsonPrimitive(outcome.status),
            "text" to JsonPrimitive(outcome.text),
        )
    }

    return lines("${outcomes.size} results", rows, dataOf("results" to JsonArray(results)))
}

private fun prepare(index: Int, call: JsonObject, session: ToolSession): PreparedCall {
    val tool = call.getValue("tool").jsonPrimitive.content
    val arguments = call["arguments"] as? JsonObject ?: JsonObject(emptyMap())

    val prepared =
        try {
            session.prepare(tool, arguments)
        } catch (error: ConduitError) {
            invalidInput("calls[$index] to $tool: ${error.message}")
        }

    if (prepared.action !is GameAction) {
        invalidInput("calls[$index]: $tool.${prepared.actionName} cannot run in a batch")
    }

    return prepared
}

private fun runInOneTick(
    server: MinecraftServer,
    calls: List<PreparedCall>,
    stopOnError: Boolean,
    policy: ToolPolicy,
): List<BatchOutcome> {
    var failed = false

    return calls.map { call ->
        if (failed && stopOnError) return@map BatchOutcome(call, "", "skipped")

        try {
            val reply = call.runGameAction(server)
            BatchOutcome(call, policy.redact(reply).text, "ok")
        } catch (exception: Exception) {
            failed = true
            BatchOutcome(call, describeFailure(exception, call.tool.name), "failed")
        }
    }
}
