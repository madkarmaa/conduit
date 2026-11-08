import type { RunRequest } from "../src/requests.ts";

/** A valid request with credentials that only look real. */
export function runRequest(changes: Partial<RunRequest> = {}): RunRequest {
    return {
        cli: "claude",
        model: "sonnet",
        effort: "medium",
        options: {},
        web: false,
        instructions: "Answer in a few words.",
        prompt: "Alex asks: hi",
        conversation: "player-1",
        mcp: { url: "http://minecraft:25585/mcp", token: "mcp-token-for-tests" },
        apiKey: "sk-key-for-tests",
        dangerouslyUseSubscriptionCredentials: false,
        timeoutSeconds: 30,
        ...changes,
    };
}
