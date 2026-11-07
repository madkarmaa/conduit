package top.madkarma.conduit.bot

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions

private typealias Subcommand = (CommandSourceStack, String?) -> Unit

/**
 * /conduit <request> asks the bot. Operators also have /conduit status, cancel, reload and forget.
 *
 * Brigadier always prefers a literal over the request argument, so a request that starts with a
 * subcommand word would reach the subcommand. The subcommand nodes therefore take the request
 * themselves: everyone but operators asks with the words, and so does anyone who writes more after
 * them, such as "/conduit status of the world".
 */
object ConduitCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("conduit")
                .then(subcommand("status", null, Subcommands::status))
                .then(subcommand("reload", null, Subcommands::reload))
                .then(subcommand("cancel", suggest("full", "chat"), Subcommands::cancel))
                .then(subcommand("forget", suggest("shared", "all"), Subcommands::forget))
                .then(
                    Commands.argument("request", StringArgumentType.greedyString()).executes {
                        ask(it.source, StringArgumentType.getString(it, "request"))
                    },
                ),
        )
    }

    private fun subcommand(
        name: String,
        targets: SuggestionProvider<CommandSourceStack>?,
        run: Subcommand,
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val node = Commands.literal(name).executes { runOrAsk(it.source, name, null, run) }

        // A target node goes first, so it wins over the request when the text is one word.
        if (targets != null) {
            node.then(
                Commands.argument("target", StringArgumentType.word()).suggests(targets).executes {
                    runOrAsk(it.source, name, StringArgumentType.getString(it, "target"), run)
                },
            )
        }

        return node.then(
            Commands.argument("request", StringArgumentType.greedyString()).executes {
                ask(it.source, "$name ${StringArgumentType.getString(it, "request")}")
            },
        )
    }

    private fun runOrAsk(
        source: CommandSourceStack,
        name: String,
        target: String?,
        run: Subcommand,
    ): Int {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return ask(source, listOfNotNull(name, target).joinToString(" "))
        }

        run(source, target)
        return 1
    }

    private fun ask(source: CommandSourceStack, text: String): Int {
        val player = source.player
        if (player == null) {
            source.sendFailure(Component.literal("Only players can ask Conduit."))
            return 0
        }

        val problem = Bot.ask(source.server, player, text) ?: return 1
        source.sendFailure(Component.literal(problem))
        return 0
    }

    // Suggests the given words and the names of the online players.
    private fun suggest(vararg words: String) =
        SuggestionProvider { context: CommandContext<CommandSourceStack>, builder ->
            SharedSuggestionProvider.suggest(
                words.toList() + context.source.onlinePlayerNames,
                builder,
            )
        }
}
