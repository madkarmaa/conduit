package top.madkarma.conduit.bot

import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.game.GameServer

/** Answers /conduit requests with the agent of the asker's mode. */
object Bot {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lines: Map<Mode, RequestLine> =
        Mode.entries.associateWith { RequestLine(scope, Answers::answer) }

    /**
     * Puts a request in line, or returns why it refuses the request. Called on the server thread.
     */
    fun ask(server: MinecraftServer, player: ServerPlayer, text: String): String? {
        val config = Conduit.config ?: return "Conduit is off until its config is fixed."
        val mode = server.modeFor(player, config) ?: return "You are not allowed to ask Conduit."
        if (lines.values.any { line -> line.has { it.asker.uuid == player.uuid } }) {
            return "Wait until Conduit answers your previous request."
        }

        val request = BotRequest(Asker(player), text, mode)
        server.playerList.broadcastSystemMessage(questionMessage(request.asker, text), false)
        lines.getValue(mode).add(request)
        return null
    }

    fun line(mode: Mode): RequestLine = lines.getValue(mode)

    /** Runs work that waits, such as on a runner, off the server thread. */
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    /**
     * Cancels the requests that match, waiting or running. Returns whether there were any. Called
     * on the server thread.
     */
    fun cancel(predicate: (BotRequest) -> Boolean): Boolean {
        val cancelled = lines.values.flatMap { it.cancel(predicate) }

        // Running requests announce their end. Waiting ones never start, so this announces them.
        val players = GameServer.current?.playerList
        cancelled
            .filter { it.job == null }
            .forEach {
                players?.broadcastSystemMessage(
                    botMessage("${it.asker.name}'s request was cancelled."),
                    false,
                )
            }

        return cancelled.isNotEmpty()
    }

    /**
     * Shows each asker the state of their request in the action bar. Called on the server thread.
     */
    fun showStatus(server: MinecraftServer) {
        for (line in lines.values) {
            val running = line.running()
            running?.let { status(server, it.asker.uuid, it.activity ?: "Thinking…") }

            line.waiting().forEachIndexed { index, request ->
                val ahead = index + if (running != null) 1 else 0
                status(
                    server,
                    request.asker.uuid,
                    if (ahead == 1) "1 request is ahead of yours…"
                    else "$ahead requests are ahead of yours…",
                )
            }
        }
    }

    /** Cancels a player's newest request. Returns whether they had one. */
    fun cancelNewest(uuid: UUID): Boolean {
        val newest =
            lines.values.firstNotNullOfOrNull { line -> line.newest { it.asker.uuid == uuid } }
                ?: return false

        return cancel { it === newest }
    }

    /** Cancels every request, such as when the server stops. */
    fun stopAll() {
        cancel { true }
    }

    private fun status(server: MinecraftServer, uuid: UUID, text: String) {
        server.playerList.getPlayer(uuid)?.sendSystemMessage(Component.literal(text), true)
    }
}
