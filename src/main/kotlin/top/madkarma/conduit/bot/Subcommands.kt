package top.madkarma.conduit.bot

import java.io.IOException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.RunnerConfig
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.service.player.resolvePlayerId

/** The operators' subcommands of /conduit. They run on the server thread. */
object Subcommands {
    /** Shows each mode's agent, its runner and its line. Never shows secrets. */
    fun status(source: CommandSourceStack) {
        val config =
            Conduit.config
                ?: return source.sendFailure(
                    Component.literal(
                        "Conduit's config is invalid. The server log names the problems.",
                    ),
                )

        Bot.launch {
            val rows = Mode.entries.map { describeMode(it, config) }
            source.server.execute { rows.forEach { source.sendSystemMessage(botMessage(it)) } }
        }
    }

    fun reload(source: CommandSourceStack) {
        val problems = Conduit.reloadConfig()
        if (problems.isEmpty()) return source.sendSystemMessage(botMessage("Reloaded the config."))

        source.sendFailure(Component.literal("The config is invalid, so the old one stays:"))
        problems.forEach { source.sendFailure(Component.literal("- $it")) }
    }

    /** Cancels the asker's own request, the running one of a mode, or a player's request. */
    fun cancel(source: CommandSourceStack, target: String?) {
        val mode = Mode.entries.firstOrNull { it.id == target }
        val ownId = source.player?.uuid

        val cancelled =
            when {
                mode != null -> Bot.cancel { it === Bot.line(mode).running() }
                target != null -> Bot.cancel { it.asker.name.equals(target, ignoreCase = true) }
                ownId != null -> Bot.cancel { it.asker.uuid == ownId }
                else -> return source.sendFailure(Component.literal("Name full, chat or a player."))
            }

        source.sendSystemMessage(
            botMessage(if (cancelled) "Cancelled." else "There was nothing to cancel."),
        )
    }

    /** Starts fresh conversations, so the agents no longer see earlier requests. */
    fun forget(source: CommandSourceStack, target: String?) {
        val described =
            try {
                forgetTarget(source, target)
            } catch (error: ConduitError) {
                return source.sendFailure(Component.literal(error.message.orEmpty()))
            }

        source.sendSystemMessage(botMessage("Started fresh conversations for $described."))
    }

    private fun forgetTarget(source: CommandSourceStack, target: String?): String {
        when (target) {
            "shared" -> Conversations.forgetShared()
            "all" -> Conversations.forgetAll()
            null -> {
                val player =
                    source.player
                        ?: throw ConduitError.InvalidInput("Name a player, shared or all.")
                Conversations.forgetPlayer(player.uuid)
                return "you"
            }
            else -> Conversations.forgetPlayer(source.server.resolvePlayerId(target))
        }
        return if (target == "all") "everyone" else target
    }

    private suspend fun describeMode(mode: Mode, config: ConduitConfig): Component {
        val agent = config.agent(mode)
        if (!agent.enabled)
            return Component.literal("${mode.id}: off").withStyle(ChatFormatting.GRAY)

        val line = Bot.line(mode)
        val asking = line.running()?.let { "answering ${it.asker.name}" } ?: "idle"
        val web = if (agent.web) "web on" else "web off"
        val credentials =
            if (agent.dangerouslyUseSubscriptionCredentials) "subscription login" else "API key"

        return Component.literal(
            "${mode.id}: ${agent.cli} ${agent.model} (${agent.effort}), $web, $credentials. Runner: " +
                "${runnerState(agent.runner, agent.cli)}. Line: $asking, ${line.waiting().size} waiting.",
        )
    }

    private suspend fun runnerState(runner: RunnerConfig, cli: String): String =
        try {
            val health = RunnerClient(runner).health()
            val version = health.versions[cli] ?: "$cli not installed yet"
            val activity = if (health.updating) "updating" else if (health.busy) "busy" else "idle"
            "$version, $activity"
        } catch (_: RunnerRefused) {
            "refused the secret"
        } catch (_: IOException) {
            "unreachable"
        }
}
