// Drives Antigravity CLI through the Agent Client Protocol SDK, with `antigravity acp`, the mode
// Google documents for driving the CLI from other programs.
import type * as acp from "@agentclientprotocol/sdk";
import { ndJsonStream } from "@agentclientprotocol/sdk";
import { type ChildProcess, spawn } from "node:child_process";
import { Readable, Writable } from "node:stream";
import { CLI_FOLDER, writeSettings } from "./antigravity-settings.ts";
import { converse } from "./antigravity-session.ts";
import type { DriverAnswer, DriverRun } from "./driver.ts";
import { stopGroup } from "./process-group.ts";

const STDERR_LIMIT = 4000;

// Google credentials that the container might have, which would override the run's own.
const GOOGLE_CREDENTIALS = [
    "GEMINI_API_KEY",
    "GOOGLE_API_KEY",
    "GOOGLE_APPLICATION_CREDENTIALS",
    "GOOGLE_CLOUD_PROJECT",
    "GOOGLE_GENAI_USE_VERTEXAI",
];

export async function runAntigravity(run: DriverRun): Promise<DriverAnswer> {
    const { env, request } = run;
    await writeSettings(env.HOME ?? "", request);

    // A worker process per session, which Antigravity needs for MCP servers that the client
    // passes.
    const cli = spawn(
        run.executable,
        ["acp", "--cli_dir_name", CLI_FOLDER, "--process-per-session"],
        {
            cwd: run.folder,
            env: cliEnvironment(env, request.apiKey),
            // A process group of its own, so stopping the CLI also stops its workers.
            detached: true,
            stdio: ["pipe", "pipe", "pipe"],
        },
    );

    let stderr = "";
    cli.stderr?.setEncoding("utf8").on("data", (chunk: string) => {
        stderr = (stderr + chunk).slice(-STDERR_LIMIT);
    });

    try {
        return await Promise.race([converse(streamOf(cli), run), ended(cli), cancelled(run.abort)]);
    } catch (error) {
        throw new Error(`${(error as Error).message}\n${stderr}`.trim());
    } finally {
        stopGroup(cli);
    }
}

function cliEnvironment(env: Record<string, string>, apiKey: string): Record<string, string> {
    const inherited = Object.entries(env).filter(([name]) => !GOOGLE_CREDENTIALS.includes(name));
    return { ...Object.fromEntries(inherited), ...(apiKey ? { GEMINI_API_KEY: apiKey } : {}) };
}

function streamOf(cli: ChildProcess): acp.Stream {
    return ndJsonStream(
        Writable.toWeb(cli.stdin!) as WritableStream<Uint8Array>,
        Readable.toWeb(cli.stdout!) as ReadableStream<Uint8Array>,
    );
}

function ended(cli: ChildProcess): Promise<never> {
    return new Promise((_, reject) => {
        cli.on("error", reject);
        cli.on("exit", (code) => reject(new Error(`Antigravity CLI exited with code ${code}`)));
    });
}

// The CLI saves the session as it goes, so stopping it keeps what the run did so far.
function cancelled(abort: AbortController): Promise<never> {
    return new Promise((_, reject) => {
        const stop = () => reject(new Error("Cancelled"));
        if (abort.signal.aborted) return stop();
        abort.signal.addEventListener("abort", stop, { once: true });
    });
}
