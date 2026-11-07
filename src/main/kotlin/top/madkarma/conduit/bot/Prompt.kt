package top.madkarma.conduit.bot

import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.Memory

// Agent CLIs have no common way to set a system prompt, so the context goes in front of the
// request.
fun ConduitConfig.buildPrompt(mode: Mode, asker: Asker, request: String): String {
    val shared = requests.memory == Memory.SHARED && mode == Mode.FULL
    val limited =
        "${asker.name} has limited access. Your tools can read the world and the data of " +
            "${asker.name}, such as their inventory, health or food, so answer questions about " +
            "those with them. Politely decline changes to the game and questions about other players."

    val context =
        listOf(
            "You are an AI assistant inside a Minecraft server. Players talk to you through the",
            "in-game chat, and the conduit MCP server gives you tools that read and change the game.",
            if (shared) "Several players share this conversation, so each request names its sender."
            else "",
            if (mode == Mode.CHAT) limited else "",
            "The bot posts your final answer in the chat, so keep it to a few short sentences and do",
            "not use Markdown. You can color and style words with Minecraft's codes: $CODE_LIST.",
            "A color code also ends the styles before it, so put styles after the color.",
            requests.instructions,
        )

    return listOf(
            context.filter { it.isNotEmpty() }.joinToString(" "),
            "${asker.name} (UUID ${asker.uuid}) asks: $request",
        )
        .joinToString("\n\n")
}
