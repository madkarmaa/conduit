package top.madkarma.conduit.bot

import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.service.player.PlayerPlace
import top.madkarma.conduit.service.player.placeOf

// The runner stops a run on its own after this many request timeouts. The mod's deadline, which
// waits for approvals, ends runs long before that by cancelling them.
private const val RUNNER_TIMEOUT_FACTOR = 4

/** What the runner gets for a request: the agent, its instructions, the prompt and MCP access. */
internal suspend fun runRequestOf(
    config: ConduitConfig,
    request: BotRequest,
    conversation: String,
    token: String,
    afterCancel: Boolean,
): RunRequest {
    val agent = config.agent(request.mode)
    val prompt =
        request.trigger?.let(::triggerPrompt)
            ?: requestPrompt(request.asker, request.text, askerPlace(request), afterCancel)

    return RunRequest(
        cli = agent.cli,
        model = agent.model,
        effort = agent.effort,
        options = agent.options,
        web = agent.web,
        instructions = config.buildInstructions(request.mode, request.asker, request.trigger),
        prompt = prompt,
        conversation = conversation,
        mcp = McpAccess(agent.mcpUrl, token),
        apiKey = agent.apiKey,
        dangerouslyUseSubscriptionCredentials = agent.dangerouslyUseSubscriptionCredentials,
        timeoutSeconds = config.requests.timeoutSeconds * RUNNER_TIMEOUT_FACTOR,
    )
}

// A missing server only costs the agent the hint, so the request goes on without it.
private suspend fun askerPlace(request: BotRequest): PlayerPlace? =
    try {
        GameServer.run { it.placeOf(request.asker.uuid) }
    } catch (exception: ConduitError) {
        null
    }
