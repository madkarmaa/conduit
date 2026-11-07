// How the runner calls each agent CLI. The flags live here, in the image, so a CLI that changes
// its flags only needs a new image, not a new mod.
//
// Every preset keeps the CLI's own tools off, so the agent cannot run commands or edit files, and
// lets it use the Conduit tools without asking. The web switch turns the CLI's web search and
// fetch tools on or off.

export const PACKAGES = {
    claude: "@anthropic-ai/claude-code",
    codex: "@openai/codex",
    opencode: "@opencode/cli",
};

// Codex features that would give the agent tools beyond the Conduit ones.
const CODEX_DISABLED_FEATURES = [
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

const CLAUDE_WEB_TOOLS = ["WebSearch", "WebFetch"];

/**
 * Returns what one run needs: the command, extra variables, files to write before it starts, and
 * where the answer ends up. Secrets go into the variables or files, never on the command line.
 */
export function buildRun(request, paths) {
    const presets = { claude: claudeRun, codex: codexRun, opencode: opencodeRun };
    return presets[request.cli](request, paths);
}

function claudeRun({ model, effort, args, web, prompt, resume, mcp }, paths) {
    const tools = web ? CLAUDE_WEB_TOOLS : [];
    const config = {
        mcpServers: {
            conduit: {
                type: "http",
                url: mcp.url,
                headers: { Authorization: `Bearer ${mcp.token}` },
            },
        },
    };

    return {
        command: [
            ...["claude", "-p", prompt],
            ...(resume ? ["--continue"] : []),
            ...["--output-format", "text"],
            ...["--mcp-config", paths.mcpConfig, "--strict-mcp-config"],
            ...["--tools", tools.join(","), "--allowedTools", ["mcp__conduit", ...tools].join(",")],
            ...["--model", model, "--effort", effort],
            ...args,
        ],
        env: {},
        files: { [paths.mcpConfig]: JSON.stringify(config) },
        answerFile: undefined,
    };
}

// -c values are TOML, and a JSON string is a valid TOML string.
function codexRun({ model, effort, args, web, prompt, resume, mcp }, paths) {
    return {
        command: [
            ...(resume ? ["codex", "exec", "resume", "--last"] : ["codex", "exec"]),
            "--skip-git-repo-check",
            ...["-c", "approval_policy='never'", "-c", `web_search='${web ? "live" : "disabled"}'`],
            ...CODEX_DISABLED_FEATURES.flatMap((feature) => ["--disable", feature]),
            ...["-c", `mcp_servers.conduit.url=${JSON.stringify(mcp.url)}`],
            ...["-c", 'mcp_servers.conduit.bearer_token_env_var="CONDUIT_MCP_TOKEN"'],
            ...["-o", paths.answer],
            ...["--model", model, "-c", `model_reasoning_effort=${JSON.stringify(effort)}`],
            ...args,
            prompt,
        ],
        env: { CONDUIT_MCP_TOKEN: mcp.token },
        files: {},
        answerFile: paths.answer,
    };
}

// OpenCode picks the effort as a variant of the model, in the form provider/model#variant. The
// session id names the conversation, and OpenCode creates the session on first use.
function opencodeRun({ model, effort, args, web, prompt, conversation, mcp }) {
    const webPermissions = web ? { webfetch: "allow", websearch: "allow" } : {};
    const config = {
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
    };

    return {
        command: [
            ...["opencode", "run", "--standalone"],
            ...["--session", `ses_conduit_${conversation}`],
            ...["--model", `${model}#${effort}`],
            ...args,
            prompt,
        ],
        env: { OPENCODE_CONFIG_CONTENT: JSON.stringify(config) },
        files: {},
        answerFile: undefined,
    };
}
