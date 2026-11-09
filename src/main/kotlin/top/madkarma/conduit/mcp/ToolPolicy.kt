package top.madkarma.conduit.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.game.invalidInput

/** The one player whose data a policy may use. */
class PlayerIdentity(val name: String, val uuid: String) {
    fun matches(reference: String): Boolean =
        reference.equals(name, ignoreCase = true) || reference.equals(uuid, ignoreCase = true)
}

/** One action that a tool call runs. */
class CalledAction(
    val tool: String,
    val action: String,
    val access: Access,
    val arguments: JsonObject,
    /** The tool marks the action as one that needs approval even when it is not destructive. */
    val needsApproval: Boolean = false,
)

/** Decides whether calls may run, and throws a ConduitError that names the reason when not. */
fun interface ApprovalGate {
    suspend fun approve(calls: List<CalledAction>)
}

/**
 * One finished call, with the arguments as the agent sent them and the text it returned or why it
 * failed. The action is null when the call failed before it found one.
 */
class RecordedCall(
    val tool: String,
    val action: CalledAction?,
    val arguments: JsonObject,
    val failed: Boolean,
    val text: String,
    /** How long the call took, approvals included. */
    val milliseconds: Long = 0,
)

/** Hears every call made with a policy. */
interface CallRecorder {
    /** Called before the call checks its approvals and runs. */
    fun started(tool: String) {}

    fun finished(call: RecordedCall)
}

/** What one token may do with the tools. */
class ToolPolicy(
    /** Shown in the log next to the calls made with this policy. */
    val name: String,
    val readOnly: Boolean = false,
    /** Only these tools, or every tool when null. */
    val allowedTools: Set<String>? = null,
    val deniedTools: Set<String> = emptySet(),
    val player: PlayerIdentity? = null,
    val approvals: ApprovalGate? = null,
    val recorder: CallRecorder? = null,
) {
    fun offers(tool: ToolDefinition): Boolean {
        if (allowedTools != null && tool.name !in allowedTools) return false
        if (tool.name in deniedTools) return false
        return player == null || !tool.seesOtherPlayers
    }

    fun allows(action: Action): Boolean = !readOnly || action.access == Access.READ

    /**
     * A policy limited to one player only accepts that player in player arguments. An empty players
     * filter would mean every player, so it has to name them.
     */
    fun checkPlayers(arguments: JsonObject) {
        val player = player ?: return
        val players = arguments["players"] as? JsonArray

        if (players != null && players.isEmpty()) invalidInput("Set players to ${player.name}")

        val named = listOfNotNull(arguments["player"]) + players.orEmpty()
        val allowed = named.all { it is JsonPrimitive && it.isString && player.matches(it.content) }
        if (!allowed) invalidInput("Only the data of ${player.name} is available")
    }
}
