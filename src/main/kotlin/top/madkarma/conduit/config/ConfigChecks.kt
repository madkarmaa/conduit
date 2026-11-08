package top.madkarma.conduit.config

import java.net.URI
import java.util.UUID

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
    problems += agentsInUse().flatMap { (key, agent) -> agentProblems("agents.$key", agent) }
    problems += accessProblems()
    problems +=
        unknownTools("tools.chat", this.tools.chat.allow.orEmpty() + this.tools.chat.deny, tools)
    problems += unknownTools("approvals", approvals.add + approvals.remove, tools)
    problems += triggerProblems()

    return problems
}

/**
 * The agents that answer anyone, by their key under agents. Chat mode also answers the players that
 * /conduit access allowed while it is disabled.
 */
fun ConduitConfig.agentsInUse(): Map<String, AgentConfig> = buildMap {
    if (agents.full.enabled) put("full", agents.full)
    if (agents.chat.enabled || access.chat.allow.isNotEmpty()) put("chat", agents.chat)
}

/** Whether a field of the config matters, so a variable it uses must be set. */
fun ConduitConfig.uses(path: String): Boolean {
    if (!path.startsWith("agents.")) return true

    val key = path.removePrefix("agents.").substringBefore('.')
    val agent = agentsInUse()[key] ?: return false

    // A subscription login stands in for the API key.
    return path != "agents.$key.apiKey" || !agent.dangerouslyUseSubscriptionCredentials
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

private fun agentProblems(path: String, agent: AgentConfig): List<String> =
    listOfNotNull(
        "$path.cli must be set to claude, codex or opencode".takeIf { agent.cli.isBlank() },
        "$path.cli must be claude, codex or opencode. For another CLI, please open an issue."
            .takeIf { agent.cli.isNotBlank() && agent.cli !in AGENT_CLIS },
        "$path.model must be set to the model, as the CLI names it"
            .takeIf { agent.model.isBlank() },
        "$path.effort must be set to the reasoning effort, as the CLI names it"
            .takeIf { agent.effort.isBlank() },
        "$path.runner.url must be an http or https URL with a host name without underscores"
            .takeIf { httpUri(agent.runner.url)?.host == null },
        "$path.runner.secret must have at least 16 letters, digits or -._~+/ characters"
            .takeIf { !RUNNER_SECRET.matches(agent.runner.secret) },
        "$path.mcpUrl must be an http or https URL".takeIf { httpUri(agent.mcpUrl) == null },
        "$path.apiKey must be set, or $path.dangerouslyUseSubscriptionCredentials turned on"
            .takeIf { agent.apiKey.isBlank() && !agent.dangerouslyUseSubscriptionCredentials },
    )

private fun ConduitConfig.accessProblems(): List<String> {
    val lists =
        mapOf(
            "access.full.allow" to access.full.allow,
            "access.full.deny" to access.full.deny,
            "access.chat.allow" to access.chat.allow,
            "access.chat.deny" to access.chat.deny,
        )

    return lists.flatMap { (path, entries) ->
        entries.mapIndexedNotNull { index, entry ->
            "$path[$index].uuid must be a UUID".takeIf { !isUuid(entry.uuid) }
        }
    }
}

private fun isUuid(text: String): Boolean = runCatching { UUID.fromString(text) }.isSuccess

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
