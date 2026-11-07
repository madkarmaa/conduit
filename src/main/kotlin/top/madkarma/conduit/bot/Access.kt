package top.madkarma.conduit.bot

import java.util.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import top.madkarma.conduit.config.AgentConfig
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.PlayerIdentity
import top.madkarma.conduit.mcp.ToolPolicy

/** Full mode controls the game. Chat mode reads it, and only the asking player's own data. */
enum class Mode {
    FULL,
    CHAT;

    val id: String = name.lowercase()
}

class Asker(val name: String, val uuid: UUID) {
    constructor(player: ServerPlayer) : this(player.gameProfile.name, player.uuid)
}

// Read tools that show nothing about other players, or only the asking player's data.
private val DEFAULT_CHAT_TOOLS =
    setOf(
        "server_info",
        "world_info",
        "game_rules",
        "get_block",
        "get_region",
        "terrain",
        "registry",
        "recipes",
        "structures",
        "get_player",
    )

fun ConduitConfig.agent(mode: Mode): AgentConfig =
    if (mode == Mode.FULL) agents.full else agents.chat

/**
 * Listed players and, unless the config turns that off, operators get full mode. Everyone else gets
 * chat mode if it is on, or no answer.
 */
fun MinecraftServer.modeFor(player: ServerPlayer, config: ConduitConfig): Mode? {
    val listed =
        config.access.allow.any {
            it.equals(player.gameProfile.name, ignoreCase = true) ||
                it.equals(player.stringUUID, ignoreCase = true)
        }
    val operator = config.access.operators && playerList.isOp(player.nameAndId())

    if ((listed || operator) && config.agents.full.enabled) return Mode.FULL
    return if (config.agents.chat.enabled) Mode.CHAT else null
}

/** What the agent of one request may do with the tools. */
fun ConduitConfig.toolPolicy(mode: Mode, asker: Asker, approvals: ApprovalGate): ToolPolicy {
    val name = "${asker.name} in ${mode.id} mode"
    if (mode == Mode.FULL) return ToolPolicy(name, readOnly = tools.readOnly, approvals = approvals)

    val chat = tools.chat
    // A deny list on its own allows every other tool, otherwise the default list applies.
    val allowed = chat.allow?.toSet() ?: DEFAULT_CHAT_TOOLS.takeIf { chat.deny.isEmpty() }

    return ToolPolicy(
        name = name,
        readOnly = true,
        allowedTools = allowed,
        deniedTools = chat.deny.toSet(),
        player = PlayerIdentity(asker.name, asker.uuid.toString()),
        approvals = approvals,
    )
}
