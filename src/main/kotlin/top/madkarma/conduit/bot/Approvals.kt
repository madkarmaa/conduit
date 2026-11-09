package top.madkarma.conduit.bot

import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
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
 * Asks for approval with `[Approve]`, `[Approve all]` and `[Deny]` buttons in the chat, which the
 * asking player and the online operators get. A click from either counts. Any modified client can
 * send any click, so each prompt has a random nonce that only the players who got it know.
 */
object Approvals {
    private val choices = setOf("approve", "approve_all", "deny")

    /** All means the rest of the request runs without asking again. */
    private class Decision(val approved: Boolean, val all: Boolean, val player: String)

    private class Pending(val asker: UUID, val decision: CompletableDeferred<Decision>)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val random = SecureRandom()

    /** The gate of one request. It asks before destructive actions and those the config adds. */
    fun gate(asker: Asker, config: ApprovalsConfig, deadline: Deadline): ApprovalGate {
        val approvedAll = AtomicBoolean(false)

        return ApprovalGate { calls ->
            val needed = calls.filter { needsApproval(it, config) }
            if (needed.isEmpty() || approvedAll.get()) return@ApprovalGate

            deadline.pause()
            try {
                if (ask(asker, needed, config.timeoutSeconds)) approvedAll.set(true)
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
        if (choice !in choices) return

        val server = player.level().server
        val allowed = player.uuid == waiting.asker || server.playerList.isOp(player.nameAndId())
        if (!allowed) return

        val decision = Decision(choice != "deny", choice == "approve_all", player.gameProfile.name)
        waiting.decision.complete(decision)
    }

    private fun needsApproval(call: CalledAction, config: ApprovalsConfig): Boolean {
        val matches = { entry: String ->
            entry == call.tool || entry == "${call.tool}.${call.action}"
        }

        val needed = call.access == Access.DESTRUCTIVE || call.needsApproval
        return (needed || config.add.any(matches)) && config.remove.none(matches)
    }

    // Returns whether the player approved the rest of the request too.
    private suspend fun ask(asker: Asker, calls: List<CalledAction>, timeoutSeconds: Int): Boolean {
        val nonce = HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))
        val actions = calls.joinToString(", ") { "${it.tool}.${it.action}" }
        val waiting = Pending(asker.uuid, CompletableDeferred())

        pending[nonce] = waiting

        try {
            GameServer.run { server ->
                server.tellAudience(asker, prompt(nonce, asker, actions, calls, timeoutSeconds))
            }

            val firstWait = timeoutSeconds / 2
            val decision =
                withTimeoutOrNull(firstWait.seconds) { waiting.decision.await() }
                    ?: remindAndWait(asker, timeoutSeconds - firstWait, waiting.decision) { left ->
                        prompt(nonce, asker, actions, calls, left)
                    }
            announce(asker, outcome(decision, actions))

            // Agents tend to send a refused call again right away, which only asks again.
            if (decision == null) {
                throw ConduitError.NotApproved(
                    "Nobody approved this in $timeoutSeconds seconds. Ask the player before " +
                        "trying it again.",
                )
            }

            if (!decision.approved) {
                throw ConduitError.NotApproved("${decision.player} denied this. Do not retry it.")
            }

            return decision.all
        } finally {
            pending.remove(nonce)
        }
    }

    private suspend fun remindAndWait(
        asker: Asker,
        seconds: Int,
        decision: CompletableDeferred<Decision>,
        prompt: (Int) -> Component,
    ): Decision? {
        GameServer.run { it.remindAudience(asker, prompt(seconds)) }
        return withTimeoutOrNull(seconds.seconds) { decision.await() }
    }

    private fun outcome(decision: Decision?, actions: String): String =
        when {
            decision == null -> "Nobody answered $actions."
            decision.all -> "${decision.player} approved $actions and the rest of the request."
            decision.approved -> "${decision.player} approved $actions."
            else -> "${decision.player} denied $actions."
        }
}
