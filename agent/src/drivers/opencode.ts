// Drives OpenCode through its client. Each run starts its own `opencode serve`, since the config
// the server starts with carries the request's MCP token.
import { OpenCode, type OpenCodeClient, type V2Event as Event } from "@opencode/client";
import type { RunRequest } from "../requests.ts";
import type { DriverAnswer, DriverRun } from "./driver.ts";
import { startServer } from "./opencode-server.ts";

const AGENT = "conduit";

export async function runOpenCode(run: DriverRun): Promise<DriverAnswer> {
    const env = { ...run.env, OPENCODE_CONFIG_CONTENT: JSON.stringify(serverConfig(run.request)) };
    const server = await startServer(run.executable, run.folder, env, run.abort.signal);

    try {
        const client = OpenCode.make({
            baseUrl: server.url,
            headers: { Authorization: server.authorization },
        });
        return await prompt(client, run);
    } finally {
        server.stop();
    }
}

function serverConfig({ model, instructions, web, mcp, apiKey, options }: RunRequest) {
    const providerId = model.split("/")[0] ?? "";
    const webPermissions = web ? { webfetch: "allow", websearch: "allow" } : {};

    return {
        ...options,
        mcp: {
            conduit: {
                type: "remote",
                url: mcp.url,
                headers: { Authorization: `Bearer ${mcp.token}` },
                oauth: false,
            },
        },
        permission: { "*": "deny", "conduit_*": "allow", ...webPermissions },
        // Without a chosen provider, web search asks which one to use, which a run cannot answer.
        websearch: web ? { provider: "random" } : false,
        agent: { [AGENT]: { prompt: instructions } },
        ...(apiKey ? { provider: { [providerId]: { options: { apiKey } } } } : {}),
    };
}

async function prompt(client: OpenCodeClient, { request, folder, sessionId, abort }: DriverRun) {
    const listening = new AbortController();
    const events = client.event.subscribe({ signal: listening.signal })[Symbol.asyncIterator]();

    try {
        // Events from before the stream connects are lost, and the answer only arrives as events.
        await nextOfType(events, "server.connected");
        const id = sessionId ?? (await createSession(client, request, folder));
        abort.signal.addEventListener(
            "abort",
            () => void client.session.interrupt({ sessionID: id }).catch(() => undefined),
            { once: true },
        );

        await client.session.prompt({ sessionID: id, text: request.prompt });
        const output = await lastAnswer(events, id);
        if (!request.resume) await client.session.remove({ sessionID: id }).catch(() => undefined);
        return { output, sessionId: id };
    } finally {
        listening.abort();
    }
}

async function createSession(client: OpenCodeClient, request: RunRequest, folder: string) {
    const [providerID = "", ...modelParts] = request.model.split("/");
    const session = await client.session.create({
        agent: AGENT,
        location: { directory: folder },
        model: { providerID, id: modelParts.join("/"), variant: request.effort },
    });
    return session.id;
}

async function nextOfType(events: AsyncIterator<Event>, type: string): Promise<void> {
    for (let next = await events.next(); !next.done; next = await events.next()) {
        if (next.value.type === type) return;
    }
    throw new Error("The OpenCode event stream ended");
}

// The answer is the text of the agent's last message, after its tool calls.
async function lastAnswer(events: AsyncIterator<Event>, sessionId: string): Promise<string> {
    let messageId = "";
    let parts: string[] = [];

    for (let next = await events.next(); !next.done; next = await events.next()) {
        const event = next.value;
        if (!("data" in event) || !event.data || !("sessionID" in event.data)) continue;
        if (event.data.sessionID !== sessionId) continue;

        if (event.type === "session.text.ended") {
            if (event.data.assistantMessageID !== messageId) parts = [];
            messageId = event.data.assistantMessageID;
            parts.push(event.data.text);
        }
        if (event.type === "session.execution.succeeded") return parts.join("\n");
        if (event.type === "session.execution.failed") throw new Error(event.data.error.message);
        if (event.type === "session.execution.interrupted") {
            throw new Error(`OpenCode stopped the run: ${event.data.reason}`);
        }
    }
    throw new Error("The OpenCode event stream ended");
}
