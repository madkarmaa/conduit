import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { catchUnwind, Err, Ok, type Result } from "results-ts";
import * as z from "zod";
import type { McpServer } from "./agents.ts";

const manifestSchema = z.object({ bin: z.record(z.string(), z.string()) });

// Resolves packages from this file, the way an import here would.
const require = createRequire(import.meta.url);

// require reports a missing package by throwing.
const findManifest = catchUnwind(
    (packageName: string): string => require.resolve(`${packageName}/package.json`),
    (thrown) => (thrown instanceof Error ? thrown.message : String(thrown)),
);

// The script behind a binary of an installed package.
export function installedBinary(packageName: string, binary: string): Result<string, string> {
    return findManifest(packageName).andThen((path) => {
        const script = manifestSchema.safeParse(require(path)).data?.bin[binary];
        if (!script) return Err(`${packageName} has no ${binary} binary`);

        return Ok(join(dirname(path), script));
    });
}

// The bot runs its own copy of the MCP server, with the runtime that runs the bot, so both always
// have the same version.
export function installedMcpServer(): Result<McpServer, string> {
    return installedBinary("@conduit/mcp", "conduit-mcp").map((script) => ({
        command: process.execPath,
        script,
    }));
}
