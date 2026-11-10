package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import top.madkarma.conduit.LOGGER
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.invalidInput

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

    /** Asks for approvals and adds the wait to the call that asks, a batch's calls included. */
    suspend fun approve(calls: List<CalledAction>) {
        val gate = policy.approvals ?: return
        if (calls.isEmpty()) return

        val startedAt = System.currentTimeMillis()
        try {
            gate.approve(calls)
        } finally {
            currentCoroutineContext()[ApprovalWait]?.add(System.currentTimeMillis() - startedAt)
        }
    }

    suspend fun call(name: String, arguments: JsonObject): CallToolResult {
        val wait = ApprovalWait()
        return withContext(wait) { record(name, arguments, wait) }
    }

    private suspend fun record(
        name: String,
        arguments: JsonObject,
        wait: ApprovalWait,
    ): CallToolResult {
        policy.recorder?.started(name)
        val startedAt = System.currentTimeMillis()

        return try {
            val prepared = prepare(name, arguments)
            if (!prepared.tool.definition.approvesItself) approve(listOf(prepared.called()))

            log(prepared, arguments)
            val reply = run(prepared)
            val called = prepared.called()
            val took = System.currentTimeMillis() - startedAt
            policy.recorder?.finished(
                RecordedCall(name, called, arguments, false, reply.text, took, wait.milliseconds),
            )
            toolResult(reply)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val failure = describeFailure(exception, name)
            val took = System.currentTimeMillis() - startedAt
            policy.recorder?.finished(
                RecordedCall(name, null, arguments, true, failure, took, wait.milliseconds),
            )
            errorResult(failure)
        }
    }

    private suspend fun run(call: PreparedCall): Reply =
        when (val action = call.action) {
            is GameAction -> GameServer.run { call.runGameAction(it) }
            is AsyncAction -> action.run(call.input, this)
        }

    // Logs the arguments as the agent sent them, without the defaults that validation adds.
    private fun log(call: PreparedCall, sent: JsonObject) {
        if (call.action.access == Access.READ) return

        val arguments = sent.toString()
        val shown =
            if (arguments.length > LOGGED_ARGUMENTS_LENGTH)
                arguments.take(LOGGED_ARGUMENTS_LENGTH) + "…"
            else arguments

        LOGGER.info("{} runs {}.{} {}", policy.name, call.tool.name, call.actionName, shown)
    }
}

/** How long one call waited for approvals, which a slow approve click adds to its time. */
private class ApprovalWait : AbstractCoroutineContextElement(ApprovalWait) {
    companion object Key : CoroutineContext.Key<ApprovalWait>

    private val total = AtomicLong()

    val milliseconds: Long
        get() = total.get()

    fun add(milliseconds: Long) {
        total.addAndGet(milliseconds)
    }
}
