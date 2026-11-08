package top.madkarma.conduit.bot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunOutcomeTest {
    @Test
    fun `an agent error shows its first line as an error`() {
        val reply =
            describe(
                RunResult(
                    exitCode = 1,
                    stderrTail = "\n  Free usage limit reached. Switch models.\nat stack line\n",
                ),
            )

        assertEquals("Free usage limit reached. Switch models.", reply.text)
        assertTrue(reply.isError)
    }

    @Test
    fun `an agent error without a message points at the log`() {
        val reply = describe(RunResult(exitCode = 1, stderrTail = " "))

        assertEquals("Something went wrong. The server log has the details.", reply.text)
        assertTrue(reply.isError)
    }

    @Test
    fun `answers and cancels are no errors`() {
        assertFalse(describe(RunResult(exitCode = 0, output = "Hi")).isError)
        assertFalse(describe(RunResult(cancelled = true)).isError)
    }
}
