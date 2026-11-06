import { presetImage, type PresetImage } from "./sandbox/images.ts";
import { RELAY_PATH } from "./sandbox/protocol.ts";

export const AGENT_NAMES = ["claude", "codex", "opencode"] as const;
export type AgentName = (typeof AGENT_NAMES)[number];

export interface McpServer {
    readonly command: string;
    readonly script: string;
}

// Inside the container, the agent reaches the Conduit MCP server through the relay.
const RELAY: McpServer = { command: "node", script: RELAY_PATH };

export interface Agent {
    // The command for one request. resume continues the latest conversation in the working
    // directory.
    readonly command: (prompt: string, resume: boolean) => string[];
    readonly env: Readonly<Record<string, string>>;
    readonly image: PresetImage;
    // Login and config files in the host's home, copied to the same place in the agent's home for
    // each run. The bot skips missing ones.
    readonly files: readonly string[];
    // Logins that the CLI stores where the bot cannot copy them. The bot exports them on the host
    // and imports them in the container before each run.
    readonly exportedLogins?: ExportedLogins;
}

export interface ExportedLogins {
    readonly export: readonly string[];
    readonly import: (file: string) => string[];
}

// Each CLI names its model and reasoning effort its own way, so the agents translate them.
export interface AgentOptions {
    readonly model: string;
    readonly effort: string;
    // Passed to the CLI as given.
    readonly args: readonly string[];
}

// The container decides what the agent can reach. The flags only stop the agent from trying tools
// that cannot work there, and make the CLI print nothing but the answer on stdout.
const AGENTS: Record<AgentName, (options: AgentOptions) => Agent> = {
    claude: ({ model, effort, args }) => {
        const flags = [
            ["--output-format", "text"],
            ["--mcp-config", mcpConfig(RELAY), "--strict-mcp-config"],
            ["--tools", "", "--allowedTools", "mcp__conduit"],
            ["--model", model, "--effort", effort],
            args,
        ].flat();

        return {
            command: (prompt, resume) => [
                ...["claude", "-p", prompt],
                ...(resume ? ["--continue"] : []),
                ...flags,
            ],
            env: {},
            image: presetImage("claude", "@anthropic-ai/claude-code"),
            files: [".claude/.credentials.json"],
        };
    },
    codex: ({ model, effort, args }) => {
        const disabled = [
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

        // -c values are TOML, and a JSON string or array of strings is valid TOML.
        const flags = [
            "--skip-git-repo-check",
            ...["-c", "approval_policy='never'", "-c", "web_search='disabled'"],
            ...disabled.flatMap((feature) => ["--disable", feature]),
            ...["-c", `mcp_servers.conduit.command=${JSON.stringify(RELAY.command)}`],
            ...["-c", `mcp_servers.conduit.args=${JSON.stringify([RELAY.script])}`],
            ...["--model", model, "-c", `model_reasoning_effort=${JSON.stringify(effort)}`],
            ...args,
        ];

        return {
            command: (prompt, resume) => [
                ...(resume ? ["codex", "exec", "resume", "--last"] : ["codex", "exec"]),
                ...flags,
                prompt,
            ],
            env: {},
            image: presetImage("codex", "@openai/codex"),
            files: [".codex/auth.json"],
        };
    },
    opencode: ({ model, effort, args }) => {
        // OpenCode picks the effort as a variant of the model, in the form provider/model#variant.
        const models = ["--model", `${model}#${effort}`];
        const config = {
            mcp: { conduit: { type: "local", command: [RELAY.command, RELAY.script] } },
            permission: { "*": "deny", "conduit_*": "allow" },
        };

        return {
            command: (prompt, resume) => [
                ...["opencode", "run", "--standalone"],
                ...(resume ? ["--continue"] : []),
                ...models,
                ...args,
                prompt,
            ],
            env: { OPENCODE_CONFIG_CONTENT: JSON.stringify(config) },
            image: presetImage("opencode", "@opencode/cli"),
            // The config defines custom providers, whose keys it reads from variables.
            files: [".config/opencode/opencode.json", ".config/opencode/opencode.jsonc"],
            // OpenCode stores its logins in the same database as every session. That database stays
            // on the host, so the bot exports the logins instead.
            exportedLogins: {
                export: ["opencode", "auth", "export"],
                import: (file) => ["opencode", "auth", "import", file],
            },
        };
    },
};

export function createAgent(name: AgentName, options: AgentOptions): Agent {
    return AGENTS[name](options);
}

// The mcpServers format that Claude Code reads.
function mcpConfig(mcp: McpServer): string {
    return JSON.stringify({
        mcpServers: { conduit: { command: mcp.command, args: [mcp.script] } },
    });
}
