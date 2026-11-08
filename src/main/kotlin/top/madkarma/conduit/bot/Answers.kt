package top.madkarma.conduit.bot

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.LOGGER
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.AccessTokens

// The runner stops a run on its own after this many request timeouts. The mod's deadline, which
// waits for approvals, ends runs long before that by cancelling them.
private const val RUNNER_TIMEOUT_FACTOR = 4

/** Runs one request with its mode's agent and posts the answer for everyone. */
object Answers {
    suspend fun answer(request: BotRequest) {
        val config = Conduit.config ?: return
        LOGGER.info("{} asks in {} mode: {}", request.asker.name, request.mode.id, request.text)
        val trace = Telemetry.start(request, config)

        val reply =
            try {
                runRequest(config, request, trace)
            } catch (exception: CancellationException) {
                trace?.finish("The request was cancelled.")
                withContext(NonCancellable) {
                    say("${request.asker.name}'s request was cancelled.")
                }
                throw exception
            }
        trace?.finish(reply)

        if (request.trigger?.config?.announce == false) {
            LOGGER.info("{} answered: {}", request.asker.name, reply)
            return
        }

        say(reply, request)
    }

    // Returns the text to post, which is the answer or what went wrong.
    private suspend fun runRequest(
        config: ConduitConfig,
        request: BotRequest,
        trace: RequestTrace?,
    ): String {
        val agent = config.agent(request.mode)
        val runner = RunnerClient(agent.runner)
        val deadline = Deadline(config.requests.timeoutSeconds)
        val progress = RequestProgress(request, trace)
        val approvals =
            progress.showingApprovals(Approvals.gate(request.asker, config.approvals, deadline))
        val policy = config.toolPolicy(request.mode, request.asker, approvals, progress)
        val token = AccessTokens.issue(policy)

        try {
            val conversation =
                request.trigger?.let { Conversations.ofTrigger(it.config.name) }
                    ?: Conversations.of(request.mode, request.asker)

            val run =
                RunRequest(
                    cli = agent.cli,
                    model = agent.model,
                    effort = agent.effort,
                    options = agent.options,
                    web = agent.web,
                    instructions =
                        config.buildInstructions(request.mode, request.asker, request.trigger),
                    prompt =
                        request.trigger?.let(::triggerPrompt)
                            ?: requestPrompt(request.asker, request.text),
                    conversation = conversation,
                    mcp = McpAccess(agent.mcpUrl, token),
                    apiKey = agent.apiKey,
                    dangerouslyUseSubscriptionCredentials =
                        agent.dangerouslyUseSubscriptionCredentials,
                    timeoutSeconds = config.requests.timeoutSeconds * RUNNER_TIMEOUT_FACTOR,
                )

            val outcome = runner.runWithDeadline(run, deadline)
            if (outcome.timedOut)
                return progress.ranOutOfTime(config.requests.timeoutSeconds, policy.name)
            return describe(outcome.result)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { runCatching { runner.cancel() } }
            throw exception
        } catch (exception: RunnerRefused) {
            LOGGER.warn("The {} runner refused the request: {}", request.mode.id, exception.message)
            // A busy or updating runner explains itself to the players.
            return if (exception.status == 409 || exception.status == 503)
                exception.message.orEmpty()
            else "The agent is not set up right. The server log has the details."
        } catch (exception: IOException) {
            LOGGER.warn(
                "Cannot reach the {} runner at {}: {}",
                request.mode.id,
                agent.runner.url,
                exception.toString(),
            )
            return "Conduit cannot reach its agent right now."
        } catch (exception: Exception) {
            LOGGER.error(
                "The {} request of {} failed",
                request.mode.id,
                request.asker.name,
                exception,
            )
            return "Something went wrong. The server log has the details."
        } finally {
            AccessTokens.revoke(token)
        }
    }

    // An answer to a player names them, like their request named the bot. Other posts do not.
    private suspend fun say(text: String, answering: BotRequest? = null) {
        val maxLength = Conduit.config?.replies?.maxLength ?: Int.MAX_VALUE
        val content = formattedText(toChatText(text, maxLength))
        val asker = answering?.asker?.takeIf { answering.trigger == null }
        val message = if (asker != null) answerMessage(asker, content) else botMessage(content)
        runCatching { GameServer.run { it.playerList.broadcastSystemMessage(message, false) } }
    }
}
