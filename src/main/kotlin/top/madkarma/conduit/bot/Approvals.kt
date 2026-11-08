package top.madkarma.conduit.bot

import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.config.ApprovalsConfig
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.Access
import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.CalledAction

internal val CLICK_ID = Conduit.id("approval")

/**
 * Asks for approval with `[Approve]` and `[Deny]` buttons in the chat, which the asking player and
 * the online operators get. A click from either counts. Any modified client can send any click, so
 * each prompt has a random nonce that only the players who got it know.
 */
object Approvals {
    private class Decision(val approved: Boolean, val player: String)

    private class Pending(val asker: UUID, val decision: CompletableDeferred<Decision>)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val random = SecureRandom()

    /** The gate of one request. It asks before destructive actions and those the config adds. */
    fun gate(asker: Asker, config: ApprovalsConfig, deadline: Deadline): ApprovalGate =
        ApprovalGate { calls ->
            val needed = calls.filter { needsApproval(it, config) }
            if (needed.isNotEmpty()) {
                deadline.pause()
                try {
                    ask(asker, needed, config.timeoutSeconds)
                } finally {
                    deadline.resume()
                }
            }
        }

    // Called from the custom click mixin on the server thread. Other clicks are not the bot's.
    @JvmStatic
    fun onCustomClick(player: ServerPlayer, id: Identifier, payload: Optional<Tag>) {
        if (id != CLICK_ID) return

        val text = (payload.getOrNull() as? StringTag)?.value() ?: return
        val choice = text.substringBefore(':')
        val waiting = pending[text.substringAfter(':')] ?: return
        if (choice != "approve" && choice != "deny") return

        val server = player.level().server
        val allowed = player.uuid == waiting.asker || server.playerList.isOp(player.nameAndId())
        if (allowed)
            waiting.decision.complete(Decision(choice == "approve", player.gameProfile.name))
    }

    private fun needsApproval(call: CalledAction, config: ApprovalsConfig): Boolean {
        val matches = { entry: String ->
            entry == call.tool || entry == "${call.tool}.${call.action}"
        }

        return (call.access == Access.DESTRUCTIVE || config.add.any(matches)) &&
            config.remove.none(matches)
    }

    private suspend fun ask(asker: Asker, calls: List<CalledAction>, timeoutSeconds: Int) {
        val nonce = HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))
        val actions = calls.joinToString(", ") { "${it.tool}.${it.action}" }
        val waiting = Pending(asker.uuid, CompletableDeferred())

        pending[nonce] = waiting

        try {
            GameServer.run { server ->
                server.tellAudience(asker, prompt(nonce, asker, actions, calls, timeoutSeconds))
            }

            val decision = withTimeoutOrNull(timeoutSeconds.seconds) { waiting.decision.await() }
            announce(asker, actions, decision?.let { it.player to it.approved })

            if (decision == null)
                throw ConduitError.NotApproved("Nobody approved this in $timeoutSeconds seconds.")

            if (!decision.approved)
                throw ConduitError.NotApproved("${decision.player} denied this.")
        } finally {
            pending.remove(nonce)
        }
    }
}
