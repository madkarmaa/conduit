package top.madkarma.conduit.mcp

import net.minecraft.server.MinecraftServer

/**
 * Destructive actions are writes that are hard to undo, such as stopping the server or kicking a
 * player.
 */
enum class Access {
    READ,
    WRITE,
    DESTRUCTIVE;

    val id: String = name.lowercase()
}

sealed interface Action {
    val access: Access
}

/** Runs on the server thread, so a batch can run several in one tick. */
class GameAction(override val access: Access, val run: (MinecraftServer, ToolInput) -> Reply) :
    Action

/** Moves between threads on its own, such as for file work or waiting. */
class AsyncAction(override val access: Access, val run: suspend (ToolInput, ToolSession) -> Reply) :
    Action

fun readAction(run: (MinecraftServer, ToolInput) -> Reply) = GameAction(Access.READ, run)

fun writeAction(run: (MinecraftServer, ToolInput) -> Reply) = GameAction(Access.WRITE, run)

fun destructiveAction(run: (MinecraftServer, ToolInput) -> Reply) =
    GameAction(Access.DESTRUCTIVE, run)

/**
 * A tool with several actions takes an action argument with the same names. The server adds it to
 * the input schema, limited to the actions the caller may use.
 */
class ToolDefinition(
    val name: String,
    val title: String,
    val description: String,
    val input: InputSchema,
    val actions: Map<String, Action>,
    /** The action to run when the call names none. */
    val defaultAction: String? = null,
    /** Returns data about players without naming them in a player argument. */
    val seesOtherPlayers: Boolean = false,
    /** Asks for approvals itself, for the calls it makes, instead of for its own action. */
    val approvesItself: Boolean = false,
)
