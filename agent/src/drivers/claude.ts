// Drives Claude Code through the Claude Agent SDK.
import {
    type EffortLevel,
    type Options,
    query,
    type SDKResultMessage,
} from "@anthropic-ai/claude-agent-sdk";
import type { DriverAnswer, DriverRun } from "./driver.ts";

const WEB_TOOLS = ["WebSearch", "WebFetch"];
const STDERR_LIMIT = 4000;

export async function runClaude(run: DriverRun): Promise<DriverAnswer> {
    let result: SDKResultMessage | undefined;
    let stderr = "";
    const options: Options = {
        ...claudeOptions(run),
        stderr: (data) => (stderr = (stderr + data).slice(-STDERR_LIMIT)),
    };

    try {
        for await (const message of query({ prompt: run.request.prompt, options })) {
            if (message.type === "result") result = message;
        }
    } catch (error) {
        // An error result also ends the stream with an exception, which says less than the result.
        if (!result) throw new Error(`${(error as Error).message}\n${stderr}`.trim());
    }

    if (!result) throw new Error(`Claude Code ended without an answer\n${stderr}`.trim());
    if (result.subtype !== "success")
        throw new Error(`${result.subtype}: ${result.errors.join("; ")}`);
    if (result.is_error) throw new Error(result.result);
    return { output: result.result, sessionId: result.session_id };
}

function claudeOptions({ request, executable, folder, env, sessionId, abort }: DriverRun): Options {
    const { model, effort, web, instructions, mcp, apiKey, options } = request;
    const tools = web ? WEB_TOOLS : [];

    return {
        ...(options as Options),
        pathToClaudeCodeExecutable: executable,
        cwd: folder,
        env: {
            ...env,
            CONDUIT_MCP_TOKEN: mcp.token,
            ...(apiKey ? { ANTHROPIC_API_KEY: apiKey } : {}),
        },
        // Rendered fresh on every run, so changed instructions reach continued conversations.
        systemPrompt: { type: "custom", prompt: instructions, snapshot: false },
        tools,
        allowedTools: ["mcp__conduit", ...tools],
        // Nobody could answer a permission prompt, so anything not allowed above is denied.
        permissionMode: "dontAsk",
        // The SDK puts the MCP config on the command line, which other processes can read, so the
        // header names the token's variable and Claude Code fills it in.
        mcpServers: {
            conduit: {
                type: "http",
                url: mcp.url,
                headers: { Authorization: "Bearer ${CONDUIT_MCP_TOKEN}" },
            },
        },
        strictMcpConfig: true,
        settingSources: [],
        model,
        effort: effort as EffortLevel,
        resume: sessionId,
        persistSession: request.resume,
        abortController: abort,
    };
}
