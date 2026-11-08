// Drives Codex through the Codex SDK.
import {
    Codex,
    type CodexOptions,
    type ModelReasoningEffort,
    type ThreadOptions,
} from "@openai/codex-sdk";
import type { DriverAnswer, DriverRun } from "./driver.ts";

// Codex features that would give the agent tools beyond the Conduit ones.
const DISABLED_FEATURES = [
    "shell_tool",
    "unified_exec",
    "apps",
    "plugins",
    "browser_use",
    "in_app_browser",
    "computer_use",
    "image_generation",
    "view_image",
];

export async function runCodex(run: DriverRun): Promise<DriverAnswer> {
    const { request, folder, sessionId, abort } = run;

    const codex = new Codex(codexOptions(run));
    const threadOptions: ThreadOptions = {
        model: request.model,
        modelReasoningEffort: request.effort as ModelReasoningEffort,
        workingDirectory: folder,
        skipGitRepoCheck: true,
        approvalPolicy: "never",
        sandboxMode: "read-only",
        webSearchMode: request.web ? "live" : "disabled",
    };

    const thread = sessionId
        ? codex.resumeThread(sessionId, threadOptions)
        : codex.startThread(threadOptions);
    const turn = await thread.run(request.prompt, { signal: abort.signal });
    return { output: turn.finalResponse, sessionId: thread.id ?? undefined };
}

function codexOptions({ request, executable, env }: DriverRun): CodexOptions {
    const { instructions, mcp, apiKey, options, timeoutSeconds } = request;

    return {
        codexPathOverride: executable,
        // The token stays out of the command line, which other processes can read.
        env: { ...env, CONDUIT_MCP_TOKEN: mcp.token },
        ...(apiKey ? { apiKey } : {}),
        config: {
            ...(options as CodexOptions["config"]),
            developer_instructions: instructions,
            mcp_servers: {
                conduit: {
                    url: mcp.url,
                    bearer_token_env_var: "CONDUIT_MCP_TOKEN",
                    // Codex stops waiting for a tool after 60 s by default, which an approval can
                    // take longer than.
                    tool_timeout_sec: timeoutSeconds,
                },
            },
            features: Object.fromEntries(DISABLED_FEATURES.map((feature) => [feature, false])),
        },
    };
}
