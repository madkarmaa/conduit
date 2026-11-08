package top.madkarma.conduit.bot

import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.CallRecorder
import top.madkarma.conduit.mcp.RecordedCall

/**
 * Follows the tool calls of one request: it shows the current one in the asker's action bar, and
 * passes the finished ones to the telemetry.
 */
class RequestProgress(private val request: BotRequest, private val trace: RequestTrace?) :
    CallRecorder {
    override fun started(tool: String) {
        request.activity = "Using $tool…"
    }

    override fun finished(call: RecordedCall) {
        request.activity = null
        trace?.record(call)
    }

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
