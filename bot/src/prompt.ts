import type { Mode } from "./access.ts";
import type { ChatMessage } from "./chat.ts";
import type { Config } from "./config.ts";
import { CODE_LIST } from "./formatting.ts";

// Agent CLIs have no common way to set a system prompt, so the context goes in front of the request.
export function buildPrompt(
    config: Pick<Config, "memory" | "instructions">,
    sender: ChatMessage,
    request: string,
    mode: Mode,
): string {
    const context = [
        "You are an AI assistant inside a Minecraft server. Players talk to you through the",
        "in-game chat, and the conduit MCP server gives you tools that read and change the game.",
        config.memory === "shared" && mode === "full"
            ? "Several players share this conversation, so each request names its sender."
            : "",
        mode === "chat"
            ? `${sender.player} has limited access. Your tools can read the world and the data of ` +
              `${sender.player}, such as their inventory, health or food, so answer questions ` +
              "about those with them. Politely decline changes to the game and questions about " +
              "other players."
            : "",
        "The bot posts your final answer in the chat, so keep it to a few short sentences and do",
        `not use Markdown. You can color and style words with Minecraft's codes: ${CODE_LIST}.`,
        "A color code also ends the styles before it, so put styles after the color.",
        config.instructions,
    ];

    return [
        context.filter((line) => line.length > 0).join(" "),
        `${sender.player} (UUID ${sender.playerUuid}) asks: ${request}`,
    ].join("\n\n");
}
