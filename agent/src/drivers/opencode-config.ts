// The config that each run's `opencode serve` starts with.
import type { RunRequest } from "../requests.ts";

/** The agent that runs the requests, with Conduit's instructions as its prompt. */
export const AGENT = "conduit";

export function serverConfig(request: RunRequest) {
    const { model, instructions, web, mcp, apiKey, options, timeoutSeconds } = request;
    const providerId = model.split("/")[0] ?? "";
    const webPermissions = web ? { webfetch: "allow", websearch: "allow" } : {};

    return {
        ...options,
        // OpenCode skips a server whose entry it cannot read, so the layout must match its schema.
        // Only mcp.servers takes the timeout per kind of request.
        mcp: {
            servers: {
                conduit: {
                    type: "remote",
                    url: mcp.url,
                    headers: { Authorization: `Bearer ${mcp.token}` },
                    oauth: false,
                    // A tool call can wait for an approval, so it may take as long as the whole run.
                    timeout: { execution: timeoutSeconds * 1000 },
                },
            },
        },
        permission: { "*": "deny", "conduit_*": "allow", ...webPermissions },
        // Without a chosen provider, web search asks which one to use, which a run cannot answer.
        websearch: web ? { provider: "random" } : false,
        agent: { [AGENT]: { prompt: instructions } },
        ...(apiKey ? { provider: { [providerId]: { options: { apiKey } } } } : {}),
    };
}
