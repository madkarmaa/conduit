package top.madkarma.conduit.bot

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.service.player.resolvePlayerId

/** The subcommands of /conduit. They run on the server thread. */
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
            val rows =
                Mode.entries.map { describeMode(it, config) } +
                    listOfNotNull(describeTriggers(config))

            source.server.execute { rows.forEach { source.sendSystemMessage(botMessage(it)) } }
        }
    }

    fun reload(source: CommandSourceStack) {
        val problems = Conduit.reloadConfig()
        if (problems.isEmpty()) return source.sendSystemMessage(botMessage("Reloaded the config."))

        source.sendFailure(Component.literal("The config is invalid, so the old one stays:"))
        problems.forEach { source.sendFailure(Component.literal("- $it")) }
    }

    /**
     * Cancels the player's newest request. Operators may name a target instead: the running request
     * of a mode, or a player's requests.
     */
    fun cancel(source: CommandSourceStack, target: String?) {
        if (target == null) return cancelOwn(source)

        val mode = Mode.entries.firstOrNull { it.id == target }
        val cancelled =
            if (mode != null) Bot.cancel { it === Bot.line(mode).running() }
            else Bot.cancel { it.asker.name.equals(target, ignoreCase = true) }

        source.sendSystemMessage(
            botMessage(if (cancelled) "Cancelled." else "There was nothing to cancel."),
        )
    }

    private fun cancelOwn(source: CommandSourceStack) {
        val player =
            source.player
                ?: return source.sendFailure(Component.literal("Name full, chat or a player."))

        if (!Bot.cancelNewest(player.uuid)) {
            return source.sendFailure(Component.literal("You have no more requests to cancel."))
        }

        source.sendSystemMessage(botMessage("Cancelled your latest request."))
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
            "all" -> Conversations.forgetAll()

            null -> {
                val player =
                    source.player ?: throw ConduitError.InvalidInput("Name a player or all.")

                Conversations.forgetPlayer(player.uuid)
                return "you"
            }

            else -> Conversations.forgetPlayer(source.server.resolvePlayerId(target))
        }

        return if (target == "all") "everyone" else target
    }
}
