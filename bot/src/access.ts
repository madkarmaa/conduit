import { call, type CallError, type ConduitClients } from "@conduit/client";
import { Ok, type AsyncResult } from "results-ts";
import type { ChatMessage } from "./chat.ts";
import { chatAgent, type Config } from "./config.ts";

// Full mode controls the game. Chat mode lets everyone else read it, but only their own player data.
export type Mode = "full" | "chat";

// Read tools that show nothing about other players, or only the asking player's data.
export const DEFAULT_CHAT_TOOLS = [
    "server_info",
    "world_info",
    "game_rules",
    "get_block",
    "get_region",
    "terrain",
    "registry",
    "recipes",
    "structures",
    "get_player",
];

// Listed players and, unless the config turns that off, operators get full mode. Everyone else gets
// chat mode if it is on, or no answer.
export function accessMode(
    players: ConduitClients["player"],
    config: Pick<Config, "access" | "agents">,
    sender: ChatMessage,
): AsyncResult<Mode | undefined, CallError> {
    const { allow, operators } = config.access;
    const fallback: Mode | undefined = chatAgent(config) ? "chat" : undefined;

    const listed = allow.some(
        (entry) =>
            entry.toLowerCase() === sender.playerUuid.toLowerCase() ||
            entry.toLowerCase() === sender.player.toLowerCase(),
    );

    if (listed || !operators) {
        const mode: Mode | undefined = listed ? "full" : fallback;
        return Ok(mode).mapAsync((value) => Promise.resolve(value));
    }

    return call(players, "getPlayer", { player: sender.playerUuid }).map((info) =>
        info.operator ? "full" : fallback,
    );
}

// Settings for the Conduit MCP server that serves one request.
export function toolPolicy(
    tools: Config["tools"],
    mode: Mode,
    sender: ChatMessage,
): Record<string, string> {
    if (mode === "full") return { CONDUIT_MCP_READ_ONLY: String(tools.readOnly) };

    const { allow, deny } = tools.chat;
    // A deny list on its own allows every other tool, otherwise the default list applies.
    const allowed = allow ?? (deny.length > 0 ? [] : DEFAULT_CHAT_TOOLS);

    return {
        CONDUIT_MCP_READ_ONLY: "true",
        CONDUIT_MCP_TOOLS: allowed.join(","),
        CONDUIT_MCP_DENY_TOOLS: deny.join(","),
        CONDUIT_MCP_PLAYER: [sender.player, sender.playerUuid].join(","),
    };
}
