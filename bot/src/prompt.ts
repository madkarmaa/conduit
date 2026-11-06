import type { Mode } from "./access.ts";
import type { ChatMessage } from "./chat.ts";
import type { Config } from "./config.ts";

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
            ? `${sender.player} has limited access, so your tools only read the game and only ` +
              "show their own player data. Politely decline anything else."
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
