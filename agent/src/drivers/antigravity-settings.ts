// The settings, tool hook and session bookkeeping of Antigravity CLI runs.
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import type { RunRequest } from "../requests.ts";

export { SERVER_NAME } from "./antigravity-hook.ts";

/** The folder under ~/.gemini/antigravity-cli with Conduit's own settings, logs and cache. */
export const CLI_FOLDER = "conduit";

const HOOK = join(import.meta.dir, "antigravity-hook.ts");

// Every tool call goes through the hook, which allows Conduit's tools and denies the rest.
export function hooks({ web }: RunRequest) {
    const command = [process.execPath, HOOK, ...(web ? ["--web"] : [])].join(" ");
    return {
        conduit: {
            PreToolUse: [{ matcher: "*", hooks: [{ type: "command", command, timeout: 10 }] }],
        },
    };
}

/** The session options for every new or resumed session. */
export function sessionMeta({ instructions }: RunRequest) {
    // Sent on resume as well, since the CLI does not keep it between processes.
    return { antigravity: { systemPrompt: { append: instructions } } };
}

// The runner owns these files: the settings in a folder of its own, and the hooks in the global
// configuration, which is the only place the CLI reads them from outside a project.
export async function writeSettings(home: string, request: RunRequest): Promise<void> {
    const gemini = join(home, ".gemini");
    await writeJson(join(gemini, "antigravity-cli", CLI_FOLDER, "settings.json"), request.options);
    await writeJson(join(gemini, "config", "hooks.json"), hooks(request));
}

async function writeJson(file: string, value: unknown): Promise<void> {
    await mkdir(dirname(file), { recursive: true });
    await writeFile(file, JSON.stringify(value, null, 4));
}

// A session may keep the system prompt it started with, so the saved session names the
// instructions it has, and changed instructions start a new session.
export function sessionKey(sessionId: string, instructions: string): string {
    return `${sessionId}:${digest(instructions)}`;
}

export function resumableSession(saved: string | undefined, instructions: string) {
    const [sessionId, instructionsDigest] = saved?.split(":") ?? [];
    return instructionsDigest === digest(instructions) ? sessionId : undefined;
}

function digest(text: string): string {
    return new Bun.CryptoHasher("sha256").update(text).digest("hex").slice(0, 16);
}
