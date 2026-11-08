// Starts `opencode serve` for one run and stops it afterwards.
import { type ChildProcess, spawn } from "node:child_process";
import { randomBytes } from "node:crypto";

const STARTUP_TIMEOUT_MS = 30_000;
const KILL_GRACE_MS = 5000;
const STDERR_LIMIT = 4000;

/** A running server, with the Basic credentials its client needs. */
export type OpenCodeServer = { url: string; authorization: string; stop: () => void };

export async function startServer(
    executable: string,
    folder: string,
    env: Record<string, string>,
    signal: AbortSignal,
): Promise<OpenCodeServer> {
    const password = randomBytes(24).toString("base64url");

    const server = spawn(executable, ["serve", "--hostname=127.0.0.1", "--port=0"], {
        cwd: folder,
        env: { ...env, OPENCODE_PASSWORD: password },
        // A process group of its own, so stopping the server also stops what it started.
        detached: true,
        stdio: ["ignore", "pipe", "pipe"],
    });

    try {
        const url = await serverUrl(server, signal);
        const basic = Buffer.from(`opencode:${password}`, "utf8").toString("base64");
        return { url, authorization: `Basic ${basic}`, stop: () => stopGroup(server) };
    } catch (error) {
        stopGroup(server);
        throw error;
    }
}

// The server picks a free port and prints its URL. Its other output stays out of the logs.
function serverUrl(server: ChildProcess, signal: AbortSignal): Promise<string> {
    return new Promise((resolve, reject) => {
        let stderr = "";

        const fail = (reason: string) =>
            reject(new Error(`${reason}\n${stderr}`.trim().slice(-STDERR_LIMIT)));

        const timer = setTimeout(() => fail("OpenCode did not start in time"), STARTUP_TIMEOUT_MS);

        server.stdout?.setEncoding("utf8").on("data", (chunk: string) => {
            const url = chunk.match(/listening on (http:\/\/\S+)/)?.[1];
            if (!url) return;
            clearTimeout(timer);
            resolve(url);
        });

        server.stderr?.setEncoding("utf8").on("data", (chunk: string) => {
            stderr = (stderr + chunk).slice(-STDERR_LIMIT);
        });

        server.on("error", (error) => fail(error.message));
        server.on("exit", (code) => fail(`OpenCode exited with code ${code}`));
        signal.addEventListener("abort", () => fail("Cancelled"), { once: true });
    });
}

function stopGroup(child: ChildProcess): void {
    const groupId = child.pid;
    if (groupId === undefined) return;

    const signal = (name: NodeJS.Signals) => {
        try {
            process.kill(-groupId, name);
        } catch {
            // The group already ended.
        }
    };

    signal("SIGTERM");
    setTimeout(() => signal("SIGKILL"), KILL_GRACE_MS).unref();
}
