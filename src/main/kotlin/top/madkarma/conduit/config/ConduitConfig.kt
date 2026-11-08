package top.madkarma.conduit.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    val triggers: List<TriggerConfig> = emptyList(),
    /** Writes each request and the agent's tool calls to a text file. Off unless opted in. */
    val telemetry: Boolean = false,
)

@Serializable
data class McpConfig(
    val host: String = "0.0.0.0",
    val port: Int = 25585,
    /**
     * Host names that clients use to reach the MCP server. The server refuses other Host headers,
     * which keeps web pages from reaching it through DNS rebinding.
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
            web = true,
            apiKey = $$"${CONDUIT_FULL_API_KEY}",
            runner = RunnerConfig("http://conduit-full:8080", $$"${CONDUIT_FULL_SECRET}"),
        ),
    val chat: AgentConfig =
        AgentConfig(
            web = false,
            apiKey = $$"${CONDUIT_CHAT_API_KEY}",
            runner = RunnerConfig("http://conduit-chat:8080", $$"${CONDUIT_CHAT_SECRET}"),
        ),
)

/**
 * The model and effort pass to the CLI as given, since every CLI names them its own way. The CLI,
 * model and effort have no defaults, since the provider and model are the operator's choice.
 */
@Serializable
data class AgentConfig(
    val enabled: Boolean = true,
    val cli: String = "",
    val model: String = "",
    val effort: String = "",
    /** Extra options for the CLI's SDK. Conduit's own options win over them. */
    val options: JsonObject = JsonObject(emptyMap()),
    /** Turns the CLI's own web search and fetch tools on. */
    val web: Boolean,
    /** The key of the model's provider, which bills each request. */
    val apiKey: String,
    /**
     * Lets the CLI use the subscription that its container is logged in to. The providers' terms
     * forbid this, and it can get the account banned.
     */
    val dangerouslyUseSubscriptionCredentials: Boolean = false,
    val runner: RunnerConfig,
    /** Where the CLI in the container reaches the mod's MCP server. */
    val mcpUrl: String = "http://minecraft:25585/mcp",
)

@Serializable data class RunnerConfig(val url: String, val secret: String)

/**
 * Who gets each mode. Full mode goes to operators, chat mode to everyone while it is enabled, and
 * /conduit access writes the players it lets in or keeps out of a mode.
 */
@Serializable
data class AccessConfig(
    /** Operators get full mode. */
    val operators: Boolean = true,
    val full: ModeAccess = ModeAccess(),
    val chat: ModeAccess = ModeAccess(),
)

/** A denied player never gets the mode, and an allowed one gets it while its agent is in use. */
@Serializable
data class ModeAccess(
    val allow: List<AccessEntry> = emptyList(),
    val deny: List<AccessEntry> = emptyList(),
)

/** A player by UUID. The name is the one they had when added, for people who read the file. */
@Serializable data class AccessEntry(val name: String, val uuid: String)

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
    val timeoutSeconds: Int = 900,
    /** Added to the agent's instructions, such as the server's rules. */
    val instructions: String = "",
)

@Serializable data class RepliesConfig(val maxLength: Int = 1000)

@Serializable
data class GameConfig(
    val backupDirectory: String = "backups",
    val maxStructureVolume: Long = 8_000_000,
    /** The most changed blocks that undo keeps for all recent edits together. 0 turns undo off. */
    val maxUndoBlocks: Long = 2_000_000,
)

/**
 * Starts a full mode run without a request, on game events or every few minutes. A trigger has
 * either events or everyMinutes.
 */
@Serializable
data class TriggerConfig(
    val name: String,
    val enabled: Boolean = true,
    /** Event types that start a run, such as "player_join". */
    val events: List<String> = emptyList(),
    /** Only events about these player names or UUIDs. Empty means anyone. */
    val players: List<String> = emptyList(),
    val everyMinutes: Int = 0,
    /** What the agent does when the trigger starts it. */
    val prompt: String,
    /** The shortest time between two runs. */
    val cooldownSeconds: Int = 60,
    /** Posts the agent's answer in the chat. Without it, the answer only goes to the log. */
    val announce: Boolean = true,
)
