import type { AgentConfig } from "./config.ts";

export const PRESET_NAMES = ["claude", "codex", "opencode"] as const;
type PresetName = (typeof PRESET_NAMES)[number];

// The Conduit MCP server that ships with the bot.
export interface McpServer {
    readonly command: string;
    readonly script: string;
}

export interface AgentCommand {
    readonly start: readonly string[];
    // Continues the latest conversation in the working directory. Without it, every request starts
    // a new conversation.
    readonly resume: readonly string[] | undefined;
    readonly env: Readonly<Record<string, string>>;
}

// The bot sets these for the agent, and the agent has to pass them on to the MCP server.
export const FORWARDED_VARIABLES = ["CONDUIT_ADDRESS", "CONDUIT_TOKEN", "CONDUIT_MCP_READ_ONLY"];

const PROMPT = "{prompt}";

// Each preset limits the agent to the Conduit tools as far as its CLI allows, and prints only the
// final answer on stdout.
const PRESETS: Record<PresetName, (mcp: McpServer, args: string[]) => AgentCommand> = {
    claude: (mcp, args) => {
        const flags = [
            ["--output-format", "text"],
            ["--mcp-config", mcpConfig(mcp), "--strict-mcp-config"],
            ["--tools", "", "--allowedTools", "mcp__conduit"],
            args,
        ].flat();

        return {
            start: ["claude", "-p", PROMPT, ...flags],
            resume: ["claude", "-p", PROMPT, "--continue", ...flags],
            env: {},
        };
    },
    codex: (mcp, args) => {
        // -c values are TOML, and a JSON string or array of strings is valid TOML.
        const flags = [
            "--skip-git-repo-check",
            ...["-c", "approval_policy='never'", "-c", "sandbox_mode='read-only'"],
            ...["-c", `mcp_servers.conduit.command=${JSON.stringify(mcp.command)}`],
            ...["-c", `mcp_servers.conduit.args=${JSON.stringify([mcp.script])}`],
            ...["-c", `mcp_servers.conduit.env_vars=${JSON.stringify(FORWARDED_VARIABLES)}`],
            ...args,
        ];

        return {
            start: ["codex", "exec", ...flags, PROMPT],
            resume: ["codex", "exec", "resume", "--last", ...flags, PROMPT],
            env: {},
        };
    },
    opencode: (mcp, args) => {
        const config = {
            mcp: { conduit: { type: "local", command: [mcp.command, mcp.script] } },
            permission: { bash: "deny", edit: "deny", webfetch: "deny" },
        };

        // The shared background server would not see this process's configuration and variables.
        return {
            start: ["opencode", "run", "--standalone", ...args, PROMPT],
            resume: ["opencode", "run", "--standalone", "--continue", ...args, PROMPT],
            env: { OPENCODE_CONFIG_CONTENT: JSON.stringify(config) },
        };
    },
};

export function agentCommand(config: AgentConfig, mcp: McpServer): AgentCommand {
    if ("preset" in config) return PRESETS[config.preset](mcp, config.args);

    const values = {
        mcp_config: mcpConfig(mcp),
        mcp_command: mcp.command,
        mcp_script: mcp.script,
    };
    const fillAll = (template: string[]) => template.map((part) => fill(part, values));

    return {
        start: fillAll(config.command),
        resume: config.resume && fillAll(config.resume),
        env: {},
    };
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
