package top.madkarma.conduit.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConduitConfig(
    val mcp: McpConfig = McpConfig(),
    val agents: AgentsConfig = AgentsConfig(),
    val access: AccessConfig = AccessConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val approvals: ApprovalsConfig = ApprovalsConfig(),
    val requests: RequestsConfig = RequestsConfig(),
    val replies: RepliesConfig = RepliesConfig(),
    val game: GameConfig = GameConfig(),
)

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
data class AgentsConfig(
    val full: AgentConfig =
        AgentConfig(
            cli = "claude",
            model = "sonnet",
            effort = "medium",
            web = true,
            runner = RunnerConfig("http://conduit-full:8080", $$"${CONDUIT_FULL_SECRET}"),
        ),
    val chat: AgentConfig =
        AgentConfig(
            cli = "opencode",
            model = "opencode-go/deepseek-v4.1-flash",
            effort = "high",
            web = false,
            runner = RunnerConfig("http://conduit-chat:8080", $$"${CONDUIT_CHAT_SECRET}"),
        ),
)

/** The model and effort pass to the CLI as given, since every CLI names them its own way. */
@Serializable
data class AgentConfig(
    val enabled: Boolean = true,
    val cli: String,
    val model: String,
    val effort: String,
    /** Extra arguments for the CLI. */
    val args: List<String> = emptyList(),
    /** Turns the CLI's own web search and fetch tools on. */
    val web: Boolean,
    val runner: RunnerConfig,
    /** Where the CLI in the container reaches the mod's MCP server. */
    val mcpUrl: String = "http://minecraft:25585/mcp",
)

@Serializable data class RunnerConfig(val url: String, val secret: String)

@Serializable
data class AccessConfig(
    /** Operators get full mode. */
    val operators: Boolean = true,
    /** Player names or UUIDs that get full mode. */
    val allow: List<String> = emptyList(),
)

@Serializable
data class ToolsConfig(
    /** Full mode only offers the actions that read. */
    val readOnly: Boolean = false,
    val chat: ChatToolsConfig = ChatToolsConfig(),
)

/**
 * Tools of chat mode, which only reads. Without allow, chat mode offers the default list, or every
 * tool but the denied ones when deny names any.
 */
@Serializable
data class ChatToolsConfig(val allow: List<String>? = null, val deny: List<String> = emptyList())

/**
 * Actions the bot asks about before it runs them, as "tool" or "tool.action", besides the
 * destructive ones, and those it never asks about.
 */
@Serializable
data class ApprovalsConfig(
    val timeoutSeconds: Int = 30,
    val add: List<String> = emptyList(),
    val remove: List<String> = emptyList(),
)

@Serializable
data class RequestsConfig(
    val timeoutSeconds: Int = 300,
    val memory: Memory = Memory.PLAYER,
    /** Added to the agent's instructions, such as the server's rules. */
    val instructions: String = "",
)

/** Which earlier requests full mode remembers. Chat mode always remembers the player's own. */
@Serializable
enum class Memory {
    @SerialName("player") PLAYER,
    @SerialName("shared") SHARED,
    @SerialName("none") NONE,
}

@Serializable data class RepliesConfig(val maxLength: Int = 1000)

@Serializable
data class GameConfig(
    val backupDirectory: String = "backups",
    val maxStructureVolume: Long = 8_000_000,
)
