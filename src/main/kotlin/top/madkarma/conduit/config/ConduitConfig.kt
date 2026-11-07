package top.madkarma.conduit.config

import kotlinx.serialization.Serializable

@Serializable
data class ConduitConfig(val mcp: McpConfig = McpConfig(), val game: GameConfig = GameConfig())

@Serializable
data class McpConfig(
    val host: String = "0.0.0.0",
    val port: Int = 25585,
    /**
     * Host names that clients use to reach the MCP server. Other Host headers are refused, which
     * keeps web pages from reaching the server through DNS rebinding.
     */
    val allowedHosts: List<String> =
        listOf("minecraft", "host.docker.internal", "localhost", "127.0.0.1"),
    /** Tokens for MCP hosts outside the game, such as Claude Desktop. */
    val tokens: List<TokenConfig> = emptyList(),
)

@Serializable
data class TokenConfig(
    val name: String,
    val token: String,
    val readOnly: Boolean = false,
    /** Only these tools, or every tool when empty. */
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
)

@Serializable
data class GameConfig(
    val backupDirectory: String = "backups",
    val maxStructureVolume: Long = 8_000_000,
)

/** Returns what is wrong with the config. Tool names are checked against the given names. */
fun ConduitConfig.problems(toolNames: Set<String>): List<String> {
    val problems = mutableListOf<String>()
    val check = { valid: Boolean, problem: String -> if (!valid) problems += problem }

    check(mcp.port in 1..65535, "mcp.port must be between 1 and 65535")
    check(mcp.allowedHosts.isNotEmpty(), "mcp.allowedHosts must name at least one host")
    check(game.maxStructureVolume > 0, "game.maxStructureVolume must be positive")

    mcp.tokens.forEachIndexed { index, token ->
        val path = "mcp.tokens[$index]"
        check(token.name.isNotBlank(), "$path.name must not be empty")
        check(token.token.isNotBlank(), "$path.token must not be empty")
        for (tool in token.allow + token.deny) check(
            tool in toolNames,
            "$path names unknown tool $tool",
        )
    }

    check(mcp.tokens.distinctBy { it.name }.size == mcp.tokens.size, "mcp.tokens names repeat")
    check(mcp.tokens.distinctBy { it.token }.size == mcp.tokens.size, "mcp.tokens values repeat")
    return problems
}
