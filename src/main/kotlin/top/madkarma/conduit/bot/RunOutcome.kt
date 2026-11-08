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

/** What the bot posts after a request: the answer, or an error that shows in red. */
internal class BotReply(val text: String, val isError: Boolean = false)

/** The reply to a run that ended in time. */
internal fun describe(result: RunResult): BotReply =
    when {
        result.cancelled -> BotReply("The request was cancelled.")

        result.exitCode != 0 -> {
            LOGGER.warn("The agent exited with code {}: {}", result.exitCode, result.stderrTail)
            // The runner takes the API key and the MCP token out of the error.
            val error =
                result.stderrTail.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            BotReply(
                error ?: "Something went wrong. The server log has the details.",
                isError = true,
            )
        }

        else -> BotReply(result.output.trim().ifEmpty { "Done." })
    }
