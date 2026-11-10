package top.madkarma.conduit.bot

import java.util.Locale
import top.madkarma.conduit.mcp.RecordedCall

// Below this, the gate decided without asking anyone.
private const val SHOWN_APPROVAL_MS = 100

/** The call's time, and how much of it waited for an approve click, apart from the tool's own. */
internal fun describeTime(call: RecordedCall): String {
    val time = "${call.milliseconds} ms"
    if (call.approvalMilliseconds < SHOWN_APPROVAL_MS) return time
    val seconds = "%.1f".format(Locale.ROOT, call.approvalMilliseconds / 1000.0)
    return "$time, $seconds s of it waiting for approval"
}
