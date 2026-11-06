import { existsSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { catchUnwindAsync, type AsyncResult } from "results-ts";
import type { Mode } from "./access.ts";
import type { ChatMessage } from "./chat.ts";
import type { Memory } from "./config.ts";

export type StateError = { readonly kind: "state"; readonly message: string };

export interface Conversation {
    readonly directory: string;
    // Whether an earlier request already started this conversation.
    readonly started: boolean;
}

// Agent CLIs keep their conversations in their home and per working directory, so each
// conversation gets its own directory, mounted as the agent's home.
export class Conversations {
    private readonly queues = new Map<string, Promise<void>>();

    constructor(
        private readonly stateDirectory: string,
        private readonly memory: Memory,
    ) {}

    // Chat mode never shares a conversation, with other players or with full mode, since it holds
    // the player's own data.
    key(sender: ChatMessage, mode: Mode): string {
        if (mode === "chat") return `chat-${sender.playerUuid}`;
        return this.memory === "shared" ? "shared" : sender.playerUuid;
    }

    open(key: string): AsyncResult<Conversation, StateError> {
        const directory = this.directory(key);
        const started = this.memory !== "none" && existsSync(this.marker(key));

        return createDirectory(directory, { recursive: true }).map(() => ({ directory, started }));
    }

    markStarted(key: string): AsyncResult<void, StateError> {
        return createFile(this.marker(key), "");
    }

    // Requests in one conversation run one at a time, since each continues the one before it.
    // Returns whether the task has to wait for an earlier request.
    enqueue(key: string, task: () => PromiseLike<void>): boolean {
        const previous = this.queues.get(key);
        const next = (previous ?? Promise.resolve()).then(task).then(() => {
            if (this.queues.get(key) === next) this.queues.delete(key);
        });

        this.queues.set(key, next);
        return previous !== undefined;
    }

    private directory(key: string): string {
        return join(this.stateDirectory, "conversations", key);
    }

    // The marker sits next to the agent's working directory, out of its reach.
    private marker(key: string): string {
        return `${this.directory(key)}.started`;
    }
}

function stateError(thrown: unknown): StateError {
    return { kind: "state", message: thrown instanceof Error ? thrown.message : String(thrown) };
}

// Node reports file system failures only by rejecting.
const createDirectory = catchUnwindAsync(mkdir, stateError);
const createFile = catchUnwindAsync(writeFile, stateError);
