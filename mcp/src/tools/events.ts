import * as z from "zod";
import { EVENT_NAMES, payloadOf } from "../event-types.ts";
import type { EventFeed } from "../events.ts";
import { compact, lines } from "../format.ts";
import { defineTool } from "../tool.ts";
import { describeEntity } from "./entities.ts";

// Damage, death and attack events carry the full entity; one line is enough to read them.
const ENTITY_FIELDS = new Set(["entity", "target"]);

export function eventTools(feed: EventFeed) {
    return [
        defineTool({
            name: "wait_for_events",
            title: "Wait for events",
            description:
                "Returns game events newer than a cursor, waiting up to timeout_seconds for one to " +
                "happen. Pass the returned next cursor as after to continue where the last call " +
                "stopped. Events are recorded from the first call on. Event types: " +
                `${EVENT_NAMES.join(", ")}. events_dropped reports events the server lost.`,
            input: {
                after: z.int().min(0).default(0).describe("Cursor from the previous call."),
                types: z.array(z.enum(EVENT_NAMES)).default([]).describe("Empty means every type."),
                players: z
                    .array(z.string())
                    .default([])
                    .describe("Only events about these players or entities, by name or UUID."),
                limit: z.int().min(1).max(200).default(50),
                timeout_seconds: z.number().min(0).max(60).default(10),
            },
            actions: {
                wait: {
                    access: "read",
                    run: (input) =>
                        feed
                            .poll({
                                after: input.after,
                                names: input.types,
                                players: input.players,
                                limit: input.limit,
                                timeoutMs: input.timeout_seconds * 1000,
                            })
                            .map((page) => {
                                const rows = page.events.map(
                                    ({ cursor, name, event }) =>
                                        `#${cursor} tick ${event.tick} ${name} ` +
                                        JSON.stringify(
                                            compact(payloadOf(event)),
                                            summarizeEntities,
                                        ),
                                );
                                const expired = page.expired
                                    ? `; ${page.expired} older events expired unread`
                                    : "";
                                return lines(
                                    `${page.events.length} events, next cursor ${page.nextCursor}${expired}`,
                                    rows,
                                    {
                                        nextCursor: page.nextCursor,
                                        expired: page.expired,
                                        events: page.events.map(({ cursor, name, event }) => ({
                                            cursor,
                                            type: name,
                                            tick: event.tick,
                                            payload: compact(payloadOf(event)),
                                        })),
                                    },
                                );
                            }),
                },
            },
        }),
    ];
}

function summarizeEntities(key: string, value: unknown): unknown {
    if (!ENTITY_FIELDS.has(key) || typeof value !== "object" || value === null) return value;
    return describeEntity({ uuid: "", type: "", dimension: "", position: null, ...value });
}
