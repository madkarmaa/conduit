import type { ChatMessage } from "./chat.ts";
import type { Config } from "./config.ts";

// Agent CLIs have no common way to set a system prompt, so the context goes in front of the request.
export function buildPrompt(
    config: Pick<Config, "memory" | "instructions">,
    sender: ChatMessage,
    request: string,
): string {
    const context = [
        "You are an AI assistant inside a Minecraft server. Players talk to you through the",
        "in-game chat, and the conduit MCP server gives you tools that read and change the game.",
        config.memory === "shared"
            ? "Several players share this conversation, so each request names its sender."
            : "",
        "Your final answer is posted in the chat as plain text, so keep it to a few short",
        "sentences and do not use Markdown.",
        config.instructions,
    ];

    return [
        context.filter((line) => line.length > 0).join(" "),
        `${sender.player} (UUID ${sender.playerUuid}) asks: ${request}`,
    ].join("\n\n");
}
