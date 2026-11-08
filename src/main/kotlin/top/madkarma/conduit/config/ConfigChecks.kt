package top.madkarma.conduit.config

import java.net.URI

private val AGENT_CLIS = setOf("claude", "codex", "opencode")

// The runner refuses other secrets, since bearer tokens may only use these characters.
private val RUNNER_SECRET = Regex("[A-Za-z0-9._~+/-]{16,}=*")

/**
 * Returns what is wrong with the config. It checks tool and action names against tools, which maps
 * each tool name to its action names.
 */
fun ConduitConfig.problems(tools: Map<String, Set<String>>): List<String> {
    val problems = mutableListOf<String>()
    val check = { valid: Boolean, problem: String -> if (!valid) problems += problem }

    check(mcp.port in 1..65535, "mcp.port must be between 1 and 65535")
    check(mcp.allowedHosts.isNotEmpty(), "mcp.allowedHosts must name at least one host")
    check(approvals.timeoutSeconds > 0, "approvals.timeoutSeconds must be positive")
    check(requests.timeoutSeconds > 0, "requests.timeoutSeconds must be positive")
    check(replies.maxLength >= 20, "replies.maxLength must be at least 20")
    check(game.maxStructureVolume > 0, "game.maxStructureVolume must be positive")
    check(game.maxUndoBlocks >= 0, "game.maxUndoBlocks must not be negative")

    problems += tokenProblems(tools.keys)
    problems +=
        agentProblems("agents.full", agents.full) + agentProblems("agents.chat", agents.chat)
    problems +=
        unknownTools("tools.chat", this.tools.chat.allow.orEmpty() + this.tools.chat.deny, tools)
    problems += unknownTools("approvals", approvals.add + approvals.remove, tools)
    problems += triggerProblems()

    return problems
}

/** Whether a field of the config matters, so a variable it uses must be set. */
fun ConduitConfig.uses(path: String): Boolean {
    val (agentPath, agent) =
        when {
            path.startsWith("agents.full") -> "agents.full" to agents.full
            path.startsWith("agents.chat") -> "agents.chat" to agents.chat
            else -> return true
        }

    // A subscription login stands in for the API key.
    if (path == "$agentPath.apiKey")
        return agent.enabled && !agent.dangerouslyUseSubscriptionCredentials

    return agent.enabled
}

private fun ConduitConfig.tokenProblems(toolNames: Set<String>): List<String> {
    val tokens = mcp.tokens
    val problems = mutableListOf<String>()

    tokens.forEachIndexed { index, token ->
        val path = "mcp.tokens[$index]"

        if (token.name.isBlank()) problems += "$path.name must not be empty"
        if (token.token.isBlank()) problems += "$path.token must not be empty"

        problems +=
            (token.allow + token.deny)
                .filter { it !in toolNames }
                .map { "$path names unknown tool $it" }
    }

    if (tokens.distinctBy { it.name }.size != tokens.size) problems += "mcp.tokens names repeat"
    if (tokens.distinctBy { it.token }.size != tokens.size) problems += "mcp.tokens values repeat"

    return problems
}

private fun agentProblems(path: String, agent: AgentConfig): List<String> {
    if (!agent.enabled) return emptyList()

    return listOfNotNull(
        "$path.cli must be claude, codex or opencode. For another CLI, please open an issue."
            .takeIf { agent.cli !in AGENT_CLIS },
        "$path.model must name the model as the CLI names it".takeIf { agent.model.isBlank() },
        "$path.effort must name the reasoning effort as the CLI names it"
            .takeIf { agent.effort.isBlank() },
        "$path.runner.url must be an http or https URL with a host name without underscores"
            .takeIf { httpUri(agent.runner.url)?.host == null },
        "$path.runner.secret must have at least 16 letters, digits or -._~+/ characters"
            .takeIf { !RUNNER_SECRET.matches(agent.runner.secret) },
        "$path.mcpUrl must be an http or https URL".takeIf { httpUri(agent.mcpUrl) == null },
        "$path.apiKey must be set, or $path.dangerouslyUseSubscriptionCredentials turned on"
            .takeIf { agent.apiKey.isBlank() && !agent.dangerouslyUseSubscriptionCredentials },
    )
}

/** Names are "tool" or "tool.action". */
private fun unknownTools(
    path: String,
    names: List<String>,
    tools: Map<String, Set<String>>,
): List<String> =
    names.filterNot { isKnownTool(it, tools) }.map { "$path names unknown tool or action $it" }

// A dot always needs an action after it, since "tool." would match nothing.
private fun isKnownTool(name: String, tools: Map<String, Set<String>>): Boolean {
    val actions = tools[name.substringBefore('.')] ?: return false
    return '.' !in name || name.substringAfter('.') in actions
}

// Java's HTTP client, which reaches the runners, also needs URI.host, which is null for host names
// with underscores. The agent CLIs reach mcpUrl with clients that accept those.
private fun httpUri(text: String): URI? {
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val isHttp = uri.scheme == "http" || uri.scheme == "https"
    return uri.takeIf { isHttp && !it.authority.isNullOrEmpty() }
}
