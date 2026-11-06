import { Err, Ok, type Result } from "results-ts";
import type { AgentConfig } from "./config.ts";
import { ASSETS_MOUNT } from "./sandbox/container.ts";
import { presetImage, type PresetImage } from "./sandbox/images.ts";

export const PRESET_NAMES = ["claude", "codex", "opencode"] as const;
type PresetName = (typeof PRESET_NAMES)[number];

export interface McpServer {
    readonly command: string;
    readonly script: string;
}

// Inside the container, the agent reaches the Conduit MCP server through the relay.
const RELAY: McpServer = { command: "node", script: `${ASSETS_MOUNT}/relay.mjs` };

export interface AgentCommand {
    readonly start: readonly string[];
    // Continues the latest conversation in the working directory. Without it, every request starts
    // a new conversation.
    readonly resume: readonly string[] | undefined;
    readonly env: Readonly<Record<string, string>>;
    // A custom image is the user's to build.
    readonly image: PresetImage | string;
    // Login files in the host's home, copied to the same place in the agent's home for each run.
    readonly files: readonly string[];
}

// Each CLI names its model and reasoning effort its own way, so the presets translate them.
export interface ModelChoice {
    readonly model?: string | undefined;
    readonly effort?: string | undefined;
}

const PROMPT = "{prompt}";

// The container is the real boundary. The flags only keep the agent from wasting turns on tools
// that cannot work there, and keep each answer on stdout.
const PRESETS: Record<PresetName, (args: string[], choice: ModelChoice) => AgentCommand> = {
    claude: (args, { model, effort }) => {
        const flags = [
            ["--output-format", "text"],
            ["--mcp-config", mcpConfig(RELAY), "--strict-mcp-config"],
            ["--tools", "", "--allowedTools", "mcp__conduit"],
            model ? ["--model", model] : [],
            effort ? ["--effort", effort] : [],
            args,
        ].flat();

        return {
            start: ["claude", "-p", PROMPT, ...flags],
            resume: ["claude", "-p", PROMPT, "--continue", ...flags],
            env: {},
            image: presetImage("claude", "@anthropic-ai/claude-code"),
            files: [".claude/.credentials.json"],
        };
    },
    codex: (args, { model, effort }) => {
        const disabled = ["shell_tool", "unified_exec", "apps", "plugins", "browser_use"];
        disabled.push("in_app_browser", "computer_use", "image_generation", "view_image");

        // -c values are TOML, and a JSON string or array of strings is valid TOML.
        const flags = [
            "--skip-git-repo-check",
            ...["-c", "approval_policy='never'", "-c", "web_search='disabled'"],
            ...disabled.flatMap((feature) => ["--disable", feature]),
            ...["-c", `mcp_servers.conduit.command=${JSON.stringify(RELAY.command)}`],
            ...["-c", `mcp_servers.conduit.args=${JSON.stringify([RELAY.script])}`],
            ...(model ? ["--model", model] : []),
            ...(effort ? ["-c", `model_reasoning_effort=${JSON.stringify(effort)}`] : []),
            ...args,
        ];

        return {
            start: ["codex", "exec", ...flags, PROMPT],
            resume: ["codex", "exec", "resume", "--last", ...flags, PROMPT],
            env: {},
            image: presetImage("codex", "@openai/codex"),
            files: [".codex/auth.json"],
        };
    },
    opencode: (args, { model, effort }) => {
        // OpenCode picks the effort as a variant of the model, in the form provider/model#variant.
        const models = model ? ["--model", effort ? `${model}#${effort}` : model] : [];
        const config = {
            mcp: { conduit: { type: "local", command: [RELAY.command, RELAY.script] } },
            permission: { "*": "deny", "conduit_*": "allow" },
        };

        return {
            start: ["opencode", "run", "--standalone", ...models, ...args, PROMPT],
            resume: ["opencode", "run", "--standalone", "--continue", ...models, ...args, PROMPT],
            env: { OPENCODE_CONFIG_CONTENT: JSON.stringify(config) },
            image: presetImage("opencode", "@opencode/cli"),
            files: [".local/share/opencode/auth.json"],
        };
    },
};

export function agentCommand(
    config: AgentConfig,
    choice: ModelChoice,
): Result<AgentCommand, string> {
    if ("preset" in config) {
        if (config.preset === "opencode" && choice.effort && !choice.model) {
            return Err(
                "OpenCode sets the effort as a variant of the model, so it needs a model too",
            );
        }
        return Ok(PRESETS[config.preset](config.args, choice));
    }

    const values: Record<string, string> = {
        mcp_config: mcpConfig(RELAY),
        mcp_command: RELAY.command,
        mcp_script: RELAY.script,
        ...(choice.model && { model: choice.model }),
        ...(choice.effort && { effort: choice.effort }),
    };
    const fillAll = (template: string[]) => template.map((part) => fill(part, values));
    const start = fillAll(config.command);
    const resume = config.resume && fillAll(config.resume);

    const unset = ["model", "effort"].filter((name) =>
        [...start, ...(resume ?? [])].some((part) => part.includes(`{${name}}`)),
    );
    if (unset.length > 0)
        return Err(
            `The agent command uses {${unset.join("} and {")}}, which the config does not set`,
        );

    return Ok({ start, resume, env: {}, image: config.image, files: [] });
}

// Placeholders without a value stay, so templates can be filled in more than one step.
export function fill(template: string, values: Readonly<Record<string, string>>): string {
    return template.replace(/\{(\w+)\}/g, (placeholder, name: string) => {
        return values[name] ?? placeholder;
    });
}

// The mcpServers format that most agent CLIs accept.
function mcpConfig(mcp: McpServer): string {
    return JSON.stringify({
        mcpServers: { conduit: { command: mcp.command, args: [mcp.script] } },
    });
}
