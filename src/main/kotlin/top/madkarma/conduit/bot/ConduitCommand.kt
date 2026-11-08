package top.madkarma.conduit.bot

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.word
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

private typealias Subcommand = (CommandSourceStack, String?) -> Unit

private val OPERATORS = Commands.hasPermission<CommandSourceStack>(Commands.LEVEL_GAMEMASTERS)

/**
 * /conduit <request> asks the bot, and /conduit cancel takes back the player's newest request.
 * Operators also have status, reload, forget and access, and cancel with a target. Other players
 * never see those, not even as suggestions.
 *
 * Brigadier always prefers a literal over the request argument, so a request that starts with a
 * subcommand word would reach the subcommand. The subcommand nodes therefore take the request
 * themselves, and pass it to the bot when more words follow than the subcommand takes, such as
 * "/conduit status of the world".
 */
object ConduitCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = ConduitNode()

        listOf(
                subcommand("status", null) { source, _ -> Subcommands.status(source) }
                    .requires(OPERATORS),
                subcommand("reload", null) { source, _ -> Subcommands.reload(source) }
                    .requires(OPERATORS),
                subcommand("cancel", suggest("full", "chat"), Subcommands::cancel),
                subcommand("forget", suggest("all"), Subcommands::forget).requires(OPERATORS),
                access(),
                Commands.argument("request", greedyString()).executes {
                    ask(it.source, getString(it, "request"))
                },
            )
            .forEach { root.addChild(it.build()) }

        dispatcher.root.addChild(root)
    }

    private fun subcommand(
        name: String,
        targets: SuggestionProvider<CommandSourceStack>?,
        run: Subcommand,
    ): LiteralArgumentBuilder<CommandSourceStack> {
        val node =
            Commands.literal(name).executes {
                run(it.source, null)
                1
            }

        // A target node goes first, so it wins over the request when the text is one word.
        if (targets != null) {
            node.then(
                Commands.argument("target", word()).requires(OPERATORS).suggests(targets).executes {
                    run(it.source, getString(it, "target"))
                    1
                },
            )
        }

        return node.then(requestAfter(name))
    }

    // /conduit access, or /conduit access allow|deny <player> full|chat.
    private fun access(): LiteralArgumentBuilder<CommandSourceStack> {
        val node =
            Commands.literal("access").requires(OPERATORS).executes {
                AccessCommand.show(it.source)
                1
            }

        for (allowed in listOf(true, false)) {
            val player = Commands.argument("player", word()).suggests(suggest())
            Mode.entries.forEach { mode ->
                player.then(
                    Commands.literal(mode.id).executes {
                        AccessCommand.change(it.source, getString(it, "player"), mode, allowed)
                        1
                    },
                )
            }
            node.then(Commands.literal(if (allowed) "allow" else "deny").then(player))
        }

        return node.then(requestAfter("access"))
    }

    // The rest of a request that starts with a subcommand word.
    private fun requestAfter(words: String): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("request", greedyString()).executes {
            ask(it.source, "$words ${getString(it, "request")}")
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

/**
 * Brigadier only tries a literal when the next word matches it, even when the player may not use
 * that literal. Other players could then not ask requests that start with an operator's subcommand
 * word, so this node tries the request argument after the literal.
 */
private class ConduitNode :
    LiteralCommandNode<CommandSourceStack>("conduit", null, { true }, null, null, false) {
    override fun getRelevantNodes(
        input: StringReader,
    ): Collection<CommandNode<CommandSourceStack>> =
        (super.getRelevantNodes(input) + children.filter { it is ArgumentCommandNode<*, *> })
            .distinct()
}
