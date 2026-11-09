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

// Clients show the tools with the server's name in front, such as conduit_edit_blocks in OpenCode
// or mcp__conduit__edit_blocks in Claude Code and Codex, and agents copy those names into a batch.
private val CLIENT_PREFIXES = listOf("mcp__conduit__", "conduit_")

private class BatchOutcome(call: PreparedCall, val text: String, val status: String) {
    val label = "${call.tool.name}.${call.actionName}"
}

val BATCH =
    ToolDefinition(
        name = "batch",
        title = "Batch",
        description =
            "Runs tool calls in order within one server tick, after checking them all. It " +
                "is not atomic, so calls before a failure stay applied. Calls that wait or " +
                "use files, such as backups, datapacks, structures and events, cannot run in " +
                "a batch.",
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
        runInOneTick(server, calls, stopOnError)
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

/**
 * The arguments of one call. Agents sometimes put them next to the tool name instead of under
 * arguments, which means the same.
 */
internal fun callArguments(call: JsonObject): JsonObject =
    call["arguments"] as? JsonObject ?: JsonObject(call - "tool")

private fun prepare(index: Int, call: JsonObject, session: ToolSession): PreparedCall {
    val tool = serverToolName(call.getValue("tool").jsonPrimitive.content, session.offered.keys)
    val arguments = callArguments(call)

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

/** The tool's own name, for a name that a client's prefix may start. */
internal fun serverToolName(name: String, offered: Set<String>): String {
    if (name in offered) return name

    return CLIENT_PREFIXES.map { name.removePrefix(it) }.firstOrNull { it in offered } ?: name
}

private fun runInOneTick(
    server: MinecraftServer,
    calls: List<PreparedCall>,
    stopOnError: Boolean,
): List<BatchOutcome> {
    var failed = false

    return calls.map { call ->
        if (failed && stopOnError) return@map BatchOutcome(call, "", "skipped")

        try {
            val reply = call.runGameAction(server)
            BatchOutcome(call, reply.text, "ok")
        } catch (exception: Exception) {
            failed = true
            BatchOutcome(call, describeFailure(exception, call.tool.name), "failed")
        }
    }
}
