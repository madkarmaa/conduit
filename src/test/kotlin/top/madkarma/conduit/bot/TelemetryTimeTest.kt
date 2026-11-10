package top.madkarma.conduit.bot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import top.madkarma.conduit.mcp.RecordedCall

class TelemetryTimeTest {
    private fun call(milliseconds: Long, approval: Long) =
        RecordedCall("run_command", null, JsonObject(emptyMap()), false, "", milliseconds, approval)

    @Test
    fun `a call that waited for an approve click shows the wait apart`() {
        assertEquals("7737 ms, 7.6 s of it waiting for approval", describeTime(call(7737, 7621)))
    }

    @Test
    fun `a call the gate let through at once shows only its time`() {
        assertEquals("12 ms", describeTime(call(12, 3)))
    }
}
