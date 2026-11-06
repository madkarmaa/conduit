import { Err, Ok, type Result } from "results-ts";
import { withoutKeys } from "./format.ts";
import { invalidInput, type ToolError } from "./grpc.ts";
import type { Reply } from "./tool.ts";

export interface ToolPolicy {
    readonly readOnly: boolean;
    // Only these tools, or all when empty.
    readonly allowedTools: readonly string[];
    readonly deniedTools: readonly string[];
    // Names and UUIDs of the one player whose data the tools may use, or none for every player.
    readonly player: readonly string[];
}

// IP addresses identify a player outside the game.
const PRIVATE_KEYS = ["ipAddress"];

export function offers(
    policy: ToolPolicy,
    tool: { name: string; seesOtherPlayers?: boolean },
): boolean {
    if (policy.allowedTools.length > 0 && !policy.allowedTools.includes(tool.name)) return false;
    if (policy.deniedTools.includes(tool.name)) return false;
    return policy.player.length === 0 || !tool.seesOtherPlayers;
}

// A server limited to one player only accepts that player in player arguments. An empty players
// filter would mean every player, so it has to name them.
export function checkPlayers(
    input: object,
    identities: readonly string[],
): Result<undefined, ToolError> {
    if (identities.length === 0) return Ok(undefined);

    const fields: { player?: unknown; players?: unknown } = input;
    const players = Array.isArray(fields.players) ? fields.players : [];
    const named = fields.player === undefined ? players : [fields.player, ...players];
    const isAllowed = (name: unknown) =>
        typeof name === "string" &&
        identities.some((identity) => identity.toLowerCase() === name.toLowerCase());

    if (Array.isArray(fields.players) && players.length === 0) {
        return Err(invalidInput(`Set players to ${identities[0]}`));
    }
    if (!named.every(isAllowed)) {
        return Err(invalidInput(`Only the data of ${identities[0]} is available`));
    }
    return Ok(undefined);
}

export function redact(reply: Reply): Reply {
    if (!reply.data) return reply;

    const data = withoutKeys(reply.data, PRIVATE_KEYS);
    const leaks = PRIVATE_KEYS.some((key) => reply.text.includes(`"${key}"`));
    return { text: leaks ? JSON.stringify(data) : reply.text, data };
}
