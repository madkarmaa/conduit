package top.madkarma.conduit.bot

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
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
                    say(BotReply("${request.asker.name}'s request was cancelled."))
                }
                throw exception
            }
        trace?.finish(if (reply.isError) "Error: ${reply.text}" else reply.text)

        if (request.trigger?.config?.announce == false) {
            LOGGER.info("{} answered: {}", request.asker.name, reply.text)
            return
        }

        say(reply, request)
    }

    // Returns what to post, which is the answer or what went wrong.
    private suspend fun runRequest(
        config: ConduitConfig,
        request: BotRequest,
        trace: RequestTrace?,
    ): BotReply {
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
                return BotReply(progress.ranOutOfTime(config.requests.timeoutSeconds, policy.name))
            return describe(outcome.result)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { runCatching { runner.cancel() } }
            throw exception
        } catch (exception: RunnerRefused) {
            LOGGER.warn("The {} runner refused the request: {}", request.mode.id, exception.message)
            // A busy or updating runner explains itself to the players.
            val text =
                if (exception.status == 409 || exception.status == 503) exception.message.orEmpty()
                else "The agent is not set up right. The server log has the details."
            return BotReply(text, isError = true)
        } catch (exception: IOException) {
            LOGGER.warn(
                "Cannot reach the {} runner at {}: {}",
                request.mode.id,
                agent.runner.url,
                exception.toString(),
            )
            return BotReply("Conduit cannot reach its agent right now.", isError = true)
        } catch (exception: Exception) {
            LOGGER.error(
                "The {} request of {} failed",
                request.mode.id,
                request.asker.name,
                exception,
            )
            return BotReply("Something went wrong. The server log has the details.", isError = true)
        } finally {
            AccessTokens.revoke(token)
        }
    }

    // An answer to a player names them, like their request named the bot. Errors and other posts
    // do not, and errors show in red.
    private suspend fun say(reply: BotReply, answering: BotRequest? = null) {
        val maxLength = Conduit.config?.replies?.maxLength ?: Int.MAX_VALUE
        val text = toChatText(reply.text, maxLength)
        val asker = answering?.asker?.takeIf { answering.trigger == null && !reply.isError }
        val message =
            when {
                reply.isError -> botMessage(Component.literal(text).withStyle(ChatFormatting.RED))
                asker != null -> answerMessage(asker, formattedText(text))
                else -> botMessage(formattedText(text))
            }
        runCatching { GameServer.run { it.playerList.broadcastSystemMessage(message, false) } }
    }
}
