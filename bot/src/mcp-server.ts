import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { catchUnwind, Err, Ok, type Result } from "results-ts";
import { z } from "zod";
import type { McpServer } from "./agents.ts";

const manifestSchema = z.object({ bin: z.object({ "conduit-mcp": z.string() }) });

const require = createRequire(import.meta.url);

// require reports a missing package by throwing.
const findManifest = catchUnwind(
    (): string => require.resolve("@conduit/mcp/package.json"),
    (thrown) => (thrown instanceof Error ? thrown.message : String(thrown)),
);

// The agent starts the MCP server with the runtime that runs the bot, so it needs no npx download.
export function bundledMcpServer(): Result<McpServer, string> {
    return findManifest().andThen((path) => {
        const manifest = manifestSchema.safeParse(require(path));
        if (!manifest.success) return Err(`${path} names no conduit-mcp binary`);

        const script = join(dirname(path), manifest.data.bin["conduit-mcp"]);
        return Ok({ command: process.execPath, script });
    });
}
