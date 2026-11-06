import type * as grpc from "@grpc/grpc-js";
import type { ConduitClients } from "conduit-client";

export type EventStream = ReturnType<ConduitClients["events"]["subscribe"]>;
export type GameEvent = EventStream extends grpc.ClientReadableStream<infer Event> ? Event : never;

// Ticks, movement, chunk loading and unloaded entities would flood the buffer, so they are left out.
export const SUBSCRIBED_TYPES = [
    "EVENT_TYPE_CHAT",
    "EVENT_TYPE_PLAYER_JOIN",
    "EVENT_TYPE_PLAYER_LEAVE",
    "EVENT_TYPE_PLAYER_RESPAWN",
    "EVENT_TYPE_BLOCK_BREAK",
    "EVENT_TYPE_BLOCK_PLACE",
    "EVENT_TYPE_BLOCK_USE",
    "EVENT_TYPE_ENTITY_DAMAGE",
    "EVENT_TYPE_ENTITY_DEATH",
    "EVENT_TYPE_ENTITY_ATTACK",
    "EVENT_TYPE_PLAYER_CHANGE_DIMENSION",
    "EVENT_TYPE_COMMAND",
    "EVENT_TYPE_SERVER_STARTED",
    "EVENT_TYPE_SERVER_STOPPING",
    "EVENT_TYPE_ENTITY_SPAWN",
    "EVENT_TYPE_ADVANCEMENT",
    "EVENT_TYPE_INVENTORY_CHANGE",
    "EVENT_TYPE_ITEM_PICKUP",
    "EVENT_TYPE_ITEM_DROP",
    "EVENT_TYPE_ITEM_CRAFT",
    "EVENT_TYPE_ITEM_SMELT",
    "EVENT_TYPE_TRADE",
] as const;

export const EVENT_NAMES = SUBSCRIBED_TYPES.map((type) =>
    type.slice("EVENT_TYPE_".length).toLowerCase(),
);

export interface BufferedEvent {
    readonly cursor: number;
    readonly name: string;
    readonly event: GameEvent;
}

export interface EventQuery {
    readonly after: number;
    readonly names: string[];
    readonly players: string[];
    readonly limit: number;
    readonly timeoutMs: number;
}

export function matches(buffered: BufferedEvent, query: EventQuery): boolean {
    if (buffered.cursor <= query.after) return false;
    if (buffered.name === "events_dropped") return true;
    if (query.names.length > 0 && !query.names.includes(buffered.name)) return false;
    return query.players.length === 0 || involvesPlayer(buffered, query.players);
}

function involvesPlayer(buffered: BufferedEvent, players: string[]): boolean {
    const payload = payloadOf(buffered.event);
    const wanted = players.map((player) => player.toLowerCase());
    const candidates = [
        payload.player,
        payload.playerUuid,
        payload.entity?.name,
        payload.entity?.uuid,
    ];
    return candidates.some(
        (candidate) => typeof candidate === "string" && wanted.includes(candidate.toLowerCase()),
    );
}

interface Payload {
    player?: string;
    playerUuid?: string;
    entity?: { name?: string; uuid?: string } | null;
}

export function payloadOf(event: GameEvent): Payload {
    const name = event.payload;
    return name ? (event[name] ?? {}) : {};
}

export function payloadName(event: GameEvent): string {
    return (event.payload ?? "unknown").replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`);
}
