package top.madkarma.conduit.bot

import java.util.Collections
import top.madkarma.conduit.mcp.Access
import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.CallRecorder
import top.madkarma.conduit.mcp.RecordedCall

/**
 * Follows the tool calls of one request: it shows the current one in the asker's action bar, keeps
 * the actions that changed something, and passes the finished calls to the telemetry.
 */
class RequestProgress(private val request: BotRequest, private val trace: RequestTrace?) :
    CallRecorder {
    private val changes = Collections.synchronizedList(mutableListOf<String>())

    override fun started(tool: String) {
        request.activity = "Using $tool…"
    }

    override fun finished(call: RecordedCall) {
        request.activity = null
        trace?.record(call)

        val action = call.action ?: return
        if (!call.failed && action.access != Access.READ)
            changes += "${action.tool}.${action.action}"
    }

    /** The actions that changed something, such as "edit_blocks.set ×2, move_entity.teleport". */
    fun changes(): List<String> =
        synchronized(changes) { changes.groupingBy { it }.eachCount() }
            .map { (action, count) -> if (count > 1) "$action ×$count" else action }

    /** The gate, which shows that the request waits while an approval prompt is open. */
    fun showingApprovals(gate: ApprovalGate) = ApprovalGate { calls ->
        val before = request.activity
        request.activity = "Waiting for approval…"
        try {
            gate.approve(calls)
        } finally {
            request.activity = before
        }
    }
}
