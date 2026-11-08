// One Antigravity CLI run over the Agent Client Protocol: sign in, open the conversation's session,
// pick the model, prompt and collect the answer.
import * as acp from "@agentclientprotocol/sdk";
import type { RunRequest } from "../requests.ts";
import { resumableSession, SERVER_NAME, sessionKey, sessionMeta } from "./antigravity-settings.ts";
import type { DriverAnswer, DriverRun } from "./driver.ts";

export function converse(stream: acp.Stream, { request, folder, sessionId }: DriverRun) {
    const answer = new Answer();

    const client = acp
        .client({ name: "conduit" })
        .onNotification(acp.methods.client.session.update, ({ params }) => answer.add(params))
        // Nobody could answer a permission prompt. The hook allows Conduit's tools and denies the
        // others, so anything that still asks is denied.
        .onRequest(acp.methods.client.session.requestPermission, () => ({
            outcome: { outcome: "cancelled" as const },
        }));

    return client.connectWith(stream, async (agent): Promise<DriverAnswer> => {
        await agent.request(acp.methods.agent.initialize, {
            protocolVersion: acp.PROTOCOL_VERSION,
            clientCapabilities: {},
        });
        // The API key comes from the environment, and a Google login from conduit-login.
        await agent.request(acp.methods.agent.authenticate, {
            methodId: request.apiKey ? "gemini-api-key" : "oauth-personal",
        });

        const id = await openSession(agent, request, folder, sessionId);
        await configure(agent, id, request);
        answer.clear();

        const { stopReason } = await agent.request(acp.methods.agent.session.prompt, {
            sessionId: id,
            prompt: [{ type: "text", text: request.prompt }],
        });
        if (stopReason !== "end_turn") throw new Error(`Antigravity CLI stopped: ${stopReason}`);

        return { output: answer.text(), sessionId: sessionKey(id, request.instructions) };
    });
}

// Each run has a new MCP token, which a resumed session takes from here.
async function openSession(
    agent: acp.ClientContext,
    request: RunRequest,
    cwd: string,
    saved: string | undefined,
): Promise<string> {
    const mcpServers = [conduitServer(request.mcp)];
    const _meta = sessionMeta(request);
    const resumable = resumableSession(saved, request.instructions);

    if (resumable) {
        try {
            await agent.request(acp.methods.agent.session.resume, {
                sessionId: resumable,
                cwd,
                mcpServers,
                _meta,
            });
            return resumable;
        } catch {
            // A session that the CLI no longer has starts over.
        }
    }

    const session = await agent.request(acp.methods.agent.session.new, {
        cwd,
        mcpServers,
        _meta,
    });
    return session.sessionId;
}

// The token goes over stdin, so it stays out of the command line, which other processes can read.
function conduitServer({ url, token }: RunRequest["mcp"]): acp.McpServer {
    return {
        type: "http",
        name: SERVER_NAME,
        url,
        headers: [{ name: "Authorization", value: `Bearer ${token}` }],
    };
}

// The model and effort are the session's model and thinking level options, which reject values
// the account does not offer.
async function configure(agent: acp.ClientContext, sessionId: string, request: RunRequest) {
    const options = [
        ["model", request.model],
        ["thought_level", request.effort],
    ];
    for (const [configId, value] of options) {
        await agent.request(acp.methods.agent.session.setConfigOption, {
            sessionId,
            configId,
            value,
        } as acp.SetSessionConfigOptionRequest);
    }
}

/** The text of the agent's last message, after its tool calls. */
class Answer {
    private parts: string[] = [];

    add({ update }: acp.SessionNotification): void {
        if (update.sessionUpdate === "tool_call") this.clear();
        if (update.sessionUpdate !== "agent_message_chunk") return;
        if (update.content.type === "text") this.parts.push(update.content.text);
    }

    clear(): void {
        this.parts = [];
    }

    text(): string {
        return this.parts.join("");
    }
}
