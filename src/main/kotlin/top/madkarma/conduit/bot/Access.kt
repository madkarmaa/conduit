package top.madkarma.conduit.bot

import java.util.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import top.madkarma.conduit.config.AgentConfig
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.ModeAccess
import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.CallRecorder
import top.madkarma.conduit.mcp.PlayerIdentity
import top.madkarma.conduit.mcp.ToolPolicy

/** Full mode controls the game. Chat mode reads it, and only the asking player's own data. */
enum class Mode {
    FULL,
    CHAT;

    val id: String = name.lowercase()
}

/** How far a player's word goes. The agent carries out the requests of operators as asked. */
enum class Role {
    OPERATOR,
    PLAYER,
}

class Asker(val name: String, val uuid: UUID, val role: Role = Role.PLAYER) {
    constructor(
        player: ServerPlayer,
        role: Role,
    ) : this(player.gameProfile.name, player.uuid, role)
}

// Read tools that show nothing about other players, or only the asking player's data.
private val DEFAULT_CHAT_TOOLS =
    setOf(
        "server_info",
        "world_info",
        "get_blocks",
        "structures",
        "entities",
        "get_player",
        "inventory",
        "game_data",
    )

fun ConduitConfig.agent(mode: Mode): AgentConfig =
    if (mode == Mode.FULL) agents.full else agents.chat

fun MinecraftServer.roleOf(player: ServerPlayer): Role =
    if (playerList.isOp(player.nameAndId())) Role.OPERATOR else Role.PLAYER

/**
 * Operators and allowed players get full mode while it is enabled. Everyone else gets chat mode
 * while it is enabled, or only the allowed players while it is not. Denied players never get a
 * mode, and a player without one gets no answer.
 */
fun ConduitConfig.modeFor(uuid: String, operator: Boolean): Mode? {
    if (agents.full.enabled && access.full.lets(uuid, operator && access.operators)) {
        return Mode.FULL
    }

    return Mode.CHAT.takeIf { access.chat.lets(uuid, agents.chat.enabled) }
}

private fun ModeAccess.lets(uuid: String, byDefault: Boolean): Boolean =
    deny.none { it.uuid.equals(uuid, ignoreCase = true) } &&
        (byDefault || allow.any { it.uuid.equals(uuid, ignoreCase = true) })

/** What the agent of one request may do with the tools. */
fun ConduitConfig.toolPolicy(
    mode: Mode,
    asker: Asker,
    approvals: ApprovalGate,
    recorder: CallRecorder? = null,
): ToolPolicy {
    val name = "${asker.name} in ${mode.id} mode"
    if (mode == Mode.FULL) {
        return ToolPolicy(
            name,
            readOnly = tools.readOnly,
            approvals = approvals,
            recorder = recorder,
        )
    }

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
        recorder = recorder,
    )
}
