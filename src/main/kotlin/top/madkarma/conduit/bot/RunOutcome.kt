package top.madkarma.conduit.bot

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.madkarma.conduit.LOGGER

/** What a run returned, and whether it ran past its deadline. */
internal class RunOutcome(val result: RunResult, pastDeadline: Boolean) {
    val timedOut = pastDeadline || result.timedOut
}

/** Runs on the runner, and cancels the run once the deadline expires. */
internal suspend fun RunnerClient.runWithDeadline(run: RunRequest, deadline: Deadline): RunOutcome =
    coroutineScope {
        val pastDeadline = AtomicBoolean(false)

        val watchdog = launch {
            deadline.awaitExpiry()
            pastDeadline.set(true)
            runCatching { cancel() }
        }

        try {
            RunOutcome(run(run), pastDeadline.get())
        } finally {
            watchdog.cancel()
        }
    }

/** The text to post for a run that ended in time. */
internal fun describe(result: RunResult): String =
    when {
        result.cancelled -> "The request was cancelled."

        result.exitCode != 0 -> {
            LOGGER.warn("The agent exited with code {}: {}", result.exitCode, result.stderrTail)
            "Something went wrong. The server log has the details."
        }

        else -> result.output.trim().ifEmpty { "Done." }
    }
