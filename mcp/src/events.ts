import type * as grpc from "@grpc/grpc-js";
import type { ConduitClients } from "conduit-client";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";
import {
    matches,
    payloadName,
    SUBSCRIBED_TYPES,
    type BufferedEvent,
    type EventQuery,
    type EventStream,
    type GameEvent,
} from "./event-types.ts";
import { fromServiceError, type ToolError } from "./grpc.ts";

const BUFFER_SIZE = 1000;

export interface EventPage {
    readonly events: BufferedEvent[];
    readonly nextCursor: number;
    // Events newer than the query's cursor that left the buffer before they were read.
    readonly expired: number;
}

// Keeps one subscription open and numbers its events, so agents can poll with a cursor.
export class EventFeed {
    private buffer: BufferedEvent[] = [];
    private lastCursor = 0;
    private stream: EventStream | undefined;
    private failure: ToolError | undefined;
    private readonly listeners = new Set<() => void>();

    constructor(private readonly client: ConduitClients["events"]) {}

    poll(query: EventQuery): AsyncResult<EventPage, ToolError> {
        this.subscribe();
        return Ok(query).andThenAsync((value) => this.collect(value));
    }

    close() {
        this.stream?.cancel();
    }

    private async collect(query: EventQuery): Promise<Result<EventPage, ToolError>> {
        const deadline = Date.now() + query.timeoutMs;
        for (;;) {
            const matching = this.buffer.filter((buffered) => matches(buffered, query));
            if (matching.length > 0 || Date.now() >= deadline)
                return Ok(this.page(query, matching));
            if (this.failure) return Err(this.failure);
            await this.nextChange(deadline - Date.now());
        }
    }

    private page(query: EventQuery, matching: BufferedEvent[]): EventPage {
        const events = matching.slice(0, query.limit);
        const oldest = this.buffer[0]?.cursor ?? this.lastCursor + 1;
        return {
            events,
            nextCursor: events.at(-1)?.cursor ?? this.lastCursor,
            expired: Math.max(0, oldest - query.after - 1),
        };
    }

    private nextChange(timeoutMs: number): Promise<void> {
        return new Promise((resolve) => {
            const finish = () => {
                clearTimeout(timer);
                this.listeners.delete(finish);
                resolve();
            };
            const timer = setTimeout(finish, timeoutMs);
            this.listeners.add(finish);
        });
    }

    private subscribe() {
        if (this.stream) return;
        this.failure = undefined;
        const stream = this.client.subscribe({ types: [...SUBSCRIBED_TYPES] });
        stream.on("data", (event: GameEvent) => this.add(event));
        stream.on("error", (error: grpc.ServiceError) =>
            this.fail(stream, fromServiceError(error)),
        );
        stream.on("end", () =>
            this.fail(stream, {
                kind: "grpc",
                status: "UNAVAILABLE",
                details: "Event stream ended",
            }),
        );
        this.stream = stream;
    }

    private add(event: GameEvent) {
        this.lastCursor += 1;
        this.buffer.push({ cursor: this.lastCursor, name: payloadName(event), event });
        if (this.buffer.length > BUFFER_SIZE) this.buffer.shift();
        this.notify();
    }

    private fail(stream: EventStream, error: ToolError) {
        if (this.stream !== stream) return;
        this.stream = undefined;
        this.failure ??= error;
        this.notify();
    }

    private notify() {
        for (const listener of this.listeners) listener();
    }
}
