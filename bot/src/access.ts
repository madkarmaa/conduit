import { call, type CallError, type ConduitClients } from "@conduit/client";
import { Ok, type AsyncResult } from "results-ts";
import type { ChatMessage } from "./chat.ts";
import type { Config } from "./config.ts";

// Listed players may always ask. Operators may ask unless the config turns that off.
export function mayAsk(
    players: ConduitClients["player"],
    config: Pick<Config, "allow" | "operators">,
    sender: ChatMessage,
): AsyncResult<boolean, CallError> {
    const listed = config.allow.some(
        (entry) =>
            entry.toLowerCase() === sender.playerUuid.toLowerCase() ||
            entry.toLowerCase() === sender.player.toLowerCase(),
    );
    if (listed || !config.operators) return Ok(listed).mapAsync((value) => Promise.resolve(value));

    return call(players, "getPlayer", { player: sender.playerUuid }).map((info) => info.operator);
}
