package top.madkarma.conduit.bot

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.config.AccessEntry
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.allowing
import top.madkarma.conduit.config.denying
import top.madkarma.conduit.config.withModeAccess
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.service.player.lastKnownName
import top.madkarma.conduit.service.player.resolvePlayerId

/** /conduit access, which lets players into a mode or keeps them out, in the config file. */
object AccessCommand {
    /** Shows who gets each mode besides its default players. */
    fun show(source: CommandSourceStack) {
        val config = Conduit.config ?: return configInvalid(source)

        Mode.entries.forEach { mode ->
            source.sendSystemMessage(botMessage(describe(mode, config)))
        }
    }

    fun change(source: CommandSourceStack, player: String, mode: Mode, allowed: Boolean) {
        val entry =
            try {
                val id = source.server.resolvePlayerId(player)
                AccessEntry(source.server.lastKnownName(id).ifEmpty { player }, id.toString())
            } catch (error: ConduitError) {
                return source.sendFailure(Component.literal(error.message.orEmpty()))
            }

        val problems = Conduit.updateConfig { written ->
            written.withModeAccess(mode.id) {
                if (allowed) it.allowing(entry) else it.denying(entry)
            }
        }

        if (problems.isNotEmpty()) {
            source.sendFailure(Component.literal("That would make the config invalid:"))
            return problems.forEach { source.sendFailure(Component.literal("- $it")) }
        }

        val verb = if (allowed) "may now use" else "may no longer use"
        source.sendSystemMessage(botMessage("${entry.name} $verb ${mode.id} mode."))
    }

    private fun describe(mode: Mode, config: ConduitConfig): String {
        val access = if (mode == Mode.FULL) config.access.full else config.access.chat
        val default =
            when (mode) {
                Mode.FULL if !config.agents.full.enabled -> "off"
                Mode.FULL if config.access.operators -> "operators by default"
                Mode.CHAT if config.agents.chat.enabled -> "everyone by default"
                else -> "nobody by default"
            }

        return "${mode.id}: $default. Allowed: ${names(access.allow)}. " +
            "Denied: ${names(access.deny)}."
    }

    private fun names(entries: List<AccessEntry>): String =
        entries.joinToString(", ") { it.name }.ifEmpty { "none" }

    private fun configInvalid(source: CommandSourceStack) =
        source.sendFailure(
            Component.literal("Conduit's config is invalid. The server log names the problems."),
        )
}
