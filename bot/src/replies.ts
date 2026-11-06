import { call, type CallError, type ConduitClients } from "@conduit/client";
import type { AsyncResult } from "results-ts";

// A player's UUID, or everyone on the server.
export type Audience = string | "everyone";

export interface Speaker {
    readonly clients: ConduitClients;
    readonly name: string;
}

// Posts a chat message under the bot's name. A message can span several lines.
export function say(
    speaker: Speaker,
    audience: Audience,
    text: string,
): AsyncResult<unknown, CallError> {
    const message = {
        json: JSON.stringify({
            text: "",
            extra: [{ text: `[${speaker.name}] `, color: "aqua" }, text],
        }),
    };

    if (audience === "everyone") return call(speaker.clients.server, "broadcast", { message });
    return call(speaker.clients.player, "sendMessage", { player: audience, message });
}

// Shows a short status in the player's action bar, where it does not clutter the chat.
export function showStatus(
    speaker: Speaker,
    player: string,
    text: string,
): AsyncResult<unknown, CallError> {
    return call(speaker.clients.player, "sendMessage", {
        player,
        message: { plain: text },
        overlay: true,
    });
}

// Agents tend to answer in Markdown, which the chat shows as literal symbols.
export function toChatText(answer: string, maxLength: number): string {
    const text = answer
        .replace(/\*\*|__|`/g, "")
        .split("\n")
        .map((line) => line.trim())
        .filter((line) => line.length > 0)
        .join("\n");

    if (text.length <= maxLength) return text;
    return `${text.slice(0, maxLength - 1).trimEnd()}…`;
}
