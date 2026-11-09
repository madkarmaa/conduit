package top.madkarma.conduit.bot

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.madkarma.conduit.config.RunnerConfig
import top.madkarma.conduit.config.VALID_CONFIG
import top.madkarma.conduit.config.withFullAgent
import top.madkarma.conduit.mcp.RecordedCall
import top.madkarma.conduit.parseJson

class RequestTraceTest {
    private val config = VALID_CONFIG.withFullAgent {
        copy(
            cli = "opencode",
            model = "deepseek-v4.1-flash",
            effort = "high",
            apiKey = "secret-key",
            runner = RunnerConfig("http://runner", "runner-secret"),
        )
    }
    private val header = telemetryHeader("1.2.0+26.3", config)
    private val request = BotRequest(Asker("Alex", UUID(0, 1)), "what time is it", Mode.FULL)

    @Test
    fun `the header names the version and both agents without their secrets`() {
        assertEquals(
            "### Conduit 1.2.0+26.3\n" +
                "### full: opencode deepseek-v4.1-flash, high effort\n" +
                "### chat: claude sonnet, medium effort\n\n",
            header,
        )
        assertFalse("secret" in header || "provider-key" in header)
    }

    @Test
    fun `a request line names the asker and mode but not the agent`() {
        val text = RequestTrace(request, header).text("Noon.")

        assertContains(text, " Alex in full mode\nRequest: what time is it\n")
        assertFalse("opencode" in text)
    }

    @Test
    fun `a call shows how long it took`() {
        val trace = RequestTrace(request, header)
        trace.record(RecordedCall("world_info", null, parseJson("{}"), false, "y=63", 120))

        assertContains(trace.text("Done."), " world_info (120 ms) {}\n  ok: y=63\n")
    }

    @Test
    fun `a run without tool calls or with an error gets a note`() {
        val quiet = RequestTrace(request, header).apply { noteRun(RunResult(exitCode = 0)) }
        assertContains(quiet.text("Hi."), "Note: the agent made no tool calls\n")

        val failed =
            RequestTrace(request, header).apply {
                noteRun(RunResult(exitCode = 1, stderrTail = "\nMCP server conduit failed\nmore"))
            }
        assertContains(
            failed.text("Error"),
            "Note: the agent exited with code 1: MCP server conduit failed\n",
        )
    }

    @Test
    fun `a cancelled run gets no note`() {
        val trace = RequestTrace(request, header).apply { noteRun(RunResult(cancelled = true)) }

        assertTrue("Note:" !in trace.text("The request was cancelled."))
    }
}
