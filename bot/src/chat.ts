import type * as grpc from "@grpc/grpc-js";
import type { ConduitClients } from "@conduit/client";
import { None, Some, type Option } from "results-ts";

type EventStream = ReturnType<ConduitClients["events"]["subscribe"]>;
type GameEvent = EventStream extends grpc.ClientReadableStream<infer Event> ? Event : never;
export type ChatMessage = NonNullable<GameEvent["chat"]>;

const RECONNECT_DELAY_MS = 5000;

export interface ChatListener {
    stop(): void;
}

// The server restarts and drops the stream from time to time, so the listener keeps reconnecting.
export function listenToChat(
    events: ConduitClients["events"],
    onMessage: (message: ChatMessage) => void,
    onDisconnect: (reason: string) => void,
): ChatListener {
    let stream: EventStream | undefined;
    let retry: NodeJS.Timeout | undefined;
    let stopped = false;

    const reconnect = (reason: string) => {
        if (stopped || retry) return;
        onDisconnect(reason);
        retry = setTimeout(() => {
            retry = undefined;
            subscribe();
        }, RECONNECT_DELAY_MS);
    };

    const subscribe = () => {
        const current = events.subscribe({ types: ["EVENT_TYPE_CHAT"] });
        current.on("data", (event: GameEvent) => {
            if (event.chat) onMessage(event.chat);
        });
        current.on("error", (error: grpc.ServiceError) => reconnect(error.details));
        current.on("end", () => reconnect("The event stream ended"));
        stream = current;
    };

    subscribe();

    return {
        stop() {
            stopped = true;
            clearTimeout(retry);
            stream?.cancel();
        },
    };
}

// A request mentions the keyword with an @ anywhere in the message, such as "@conduit make it day".
export function findRequest(message: string, keyword: string): Option<string> {
    const mention = new RegExp(`(?<![\\w@])@${keyword}\\b`, "gi");
    if (!mention.test(message)) return None();

    return Some(message.replace(mention, " ").replace(/\s+/g, " ").trim());
}
