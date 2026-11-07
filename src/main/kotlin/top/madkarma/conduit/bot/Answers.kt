package top.madkarma.conduit.bot

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.AccessTokens

private val LOGGER = LoggerFactory.getLogger("conduit")

// The runner stops a run on its own after this many request timeouts. The mod's deadline, which
// waits for approvals, ends runs long before that by cancelling them.
private const val RUNNER_TIMEOUT_FACTOR = 4

/** Runs one request with its mode's agent and posts the answer for everyone. */
object Answers {
    suspend fun answer(request: BotRequest) {
        val config = Conduit.config ?: return
        LOGGER.info("{} asks in {} mode: {}", request.asker.name, request.mode.id, request.text)

        val reply =
            try {
                runRequest(config, request)
            } catch (exception: CancellationException) {
                withContext(NonCancellable) {
                    say("${request.asker.name}'s request was cancelled.")
                }
                throw exception
            }

        say(reply)
    }

    // Returns the text to post, which is the answer or what went wrong.
    private suspend fun runRequest(config: ConduitConfig, request: BotRequest): String {
        val agent = config.agent(request.mode)
        val runner = RunnerClient(agent.runner)
        val deadline = Deadline(config.requests.timeoutSeconds)
        val approvals = Approvals.gate(request.asker, config.approvals, deadline)
        val token = AccessTokens.issue(config.toolPolicy(request.mode, request.asker, approvals))

        try {
            val conversation = Conversations.of(request.mode, config.requests.memory, request.asker)
            val run =
                RunRequest(
                    cli = agent.cli,
                    model = agent.model,
                    effort = agent.effort,
                    args = agent.args,
                    web = agent.web,
                    prompt = config.buildPrompt(request.mode, request.asker, request.text),
                    conversation = conversation.folder,
                    resume = conversation.resumable,
                    mcp = McpAccess(agent.mcpUrl, token),
                    timeoutSeconds = config.requests.timeoutSeconds * RUNNER_TIMEOUT_FACTOR,
                )

            return describe(runWithDeadline(runner, run, deadline), config)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { runCatching { runner.cancel() } }
            throw exception
        } catch (exception: RunnerRefused) {
            LOGGER.warn("The {} runner refused the request: {}", request.mode.id, exception.message)
            return if (exception.status == 503) exception.message.orEmpty()
            else "The agent is not set up right. The server log has the details."
        } catch (exception: IOException) {
            LOGGER.warn(
                "Cannot reach the {} runner at {}: {}",
                request.mode.id,
                agent.runner.url,
                exception.toString(),
            )
            return "Conduit cannot reach its agent right now."
        } finally {
            AccessTokens.revoke(token)
        }
    }

    private class Outcome(val result: RunResult, val pastDeadline: Boolean)

    private suspend fun runWithDeadline(
        runner: RunnerClient,
        run: RunRequest,
        deadline: Deadline,
    ): Outcome = coroutineScope {
        val pastDeadline = AtomicBoolean(false)
        val watchdog = launch {
            deadline.awaitExpiry()
            pastDeadline.set(true)
            runCatching { runner.cancel() }
        }

        try {
            Outcome(runner.run(run), pastDeadline.get())
        } finally {
            watchdog.cancel()
        }
    }

    private fun describe(outcome: Outcome, config: ConduitConfig): String {
        val result = outcome.result
        return when {
            outcome.pastDeadline || result.timedOut ->
                "I ran out of time after ${config.requests.timeoutSeconds} seconds."
            result.cancelled -> "The request was cancelled."
            result.exitCode != 0 -> {
                LOGGER.warn("The agent exited with code {}: {}", result.exitCode, result.stderrTail)
                "Something went wrong. The server log has the details."
            }
            else -> result.output.trim().ifEmpty { "Done." }
        }
    }

    private suspend fun say(text: String) {
        val maxLength = Conduit.config?.replies?.maxLength ?: Int.MAX_VALUE
        val message = botMessage(formattedText(toChatText(text, maxLength)))
        runCatching { GameServer.run { it.playerList.broadcastSystemMessage(message, false) } }
    }
}
