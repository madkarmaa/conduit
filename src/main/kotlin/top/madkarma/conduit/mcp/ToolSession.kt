package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.invalidInput

private val LOGGER = LoggerFactory.getLogger("conduit")

private const val LOGGED_ARGUMENTS_LENGTH = 500

/** The tools that one policy offers, and how calls to them run. */
class ToolSession(val policy: ToolPolicy, tools: List<ToolDefinition>) {
    val offered: Map<String, OfferedTool> =
        tools
            .filter { policy.offers(it) }
            .map { tool -> OfferedTool(tool, tool.actions.filterValues { policy.allows(it) }) }
            .filter { it.actions.isNotEmpty() }
            .associateBy { it.name }

    /** Checks a call against the schema and the policy. Throws a ConduitError on a problem. */
    fun prepare(name: String, arguments: JsonObject): PreparedCall {
        val tool = offered[name] ?: invalidInput("Unknown tool $name")
        val input = ToolInput(validateArguments(tool.schema, arguments), policy)
        policy.checkPlayers(input.arguments)

        val (actionName, action) = tool.select(input)
        return PreparedCall(tool, actionName, action, input)
    }

    suspend fun approve(calls: List<CalledAction>) {
        if (calls.isNotEmpty()) policy.approvals?.approve(calls)
    }

    suspend fun call(name: String, arguments: JsonObject): CallToolResult =
        try {
            val prepared = prepare(name, arguments)
            if (!prepared.tool.definition.approvesItself) approve(listOf(prepared.called()))

            log(prepared)
            toolResult(policy.redact(run(prepared)))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            errorResult(describeFailure(exception, name))
        }

    private suspend fun run(call: PreparedCall): Reply =
        when (val action = call.action) {
            is GameAction -> GameServer.run { action.run(it, call.input) }
            is AsyncAction -> action.run(call.input, this)
        }

    private fun log(call: PreparedCall) {
        if (call.action.access == Access.READ) return

        val arguments = call.input.arguments.toString()
        val shown =
            if (arguments.length > LOGGED_ARGUMENTS_LENGTH)
                arguments.take(LOGGED_ARGUMENTS_LENGTH) + "…"
            else arguments
        LOGGER.info("{} runs {}.{} {}", policy.name, call.tool.name, call.actionName, shown)
    }
}
