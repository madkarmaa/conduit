// Antigravity CLI runs this hook before each tool call. In ACP mode the CLI ignores the permission
// rules of its settings, so the hook decides which tools a run may use.
//
//   bun antigravity-hook.ts [--web]   (the tool call comes as JSON on stdin)

/** The name Conduit's MCP server goes by. */
export const SERVER_NAME = "conduit";
// The CLI names the tools of an MCP server mcp_<server>_<tool>.
const CONDUIT_TOOLS = `mcp_${SERVER_NAME}_`;
const WEB_TOOLS = ["search_web"];

export function allowsTool(name: string, web: boolean): boolean {
    return name.startsWith(CONDUIT_TOOLS) || (web && WEB_TOOLS.includes(name));
}

// A denied call fails with the reason, and the agent goes on with its turn.
export function decision(name: string, web: boolean) {
    if (allowsTool(name, web)) return { decision: "allow" };
    return { decision: "deny", reason: "Only Conduit's Minecraft tools are available here." };
}

if (import.meta.main) {
    const input = JSON.parse(await Bun.stdin.text());
    const name = String(input?.toolCall?.name ?? "");
    console.log(JSON.stringify(decision(name, process.argv.includes("--web"))));
}
