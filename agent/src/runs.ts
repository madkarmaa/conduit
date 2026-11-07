// Runs the agent CLI for one request in its conversation's folder.
import { type ChildProcess, spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { ensureInstalled } from "./installs.ts";
import { log } from "./log.ts";
import { type AgentRun, buildRun } from "./presets.ts";
import type { RunRequest } from "./requests.ts";

const HOME = process.env.HOME ?? "/home/agent";
const CONVERSATIONS = join(HOME, "conversations");
// Written into a conversation's folder after its first successful run, so later runs continue it.
const STARTED_MARKER = ".conduit-started";

const KILL_GRACE_MS = 5000;
const OUTPUT_LIMIT = 1_000_000;
const STDERR_LIMIT = 4000;

export type RunResult = {
    exitCode: number | null;
    output: string;
    stderrTail: string;
    cancelled: boolean;
    timedOut: boolean;
};

const current: { child?: ChildProcess; cancelled: boolean } = { cancelled: false };

export async function runAgent(request: RunRequest): Promise<RunResult> {
    await ensureInstalled(request.cli);

    const folder = join(CONVERSATIONS, request.conversation);
    await mkdir(folder, { recursive: true });

    const id = randomUUID();
    const paths = {
        mcpConfig: `/tmp/conduit-${id}-mcp.json`,
        answer: `/tmp/conduit-${id}-answer.txt`,
    };
    const resume = request.resume && existsSync(join(folder, STARTED_MARKER));
    const agentRun = buildRun({ ...request, resume }, paths);

    for (const [path, content] of Object.entries(agentRun.files))
        await writeFile(path, content, { mode: 0o600 });

    log(`Running ${request.cli} in ${request.conversation}${resume ? ", continuing it" : ""}`);
    const startedAt = Date.now();
    try {
        const result = await spawnAgent(agentRun, folder, request.timeoutSeconds);
        if (result.exitCode === 0) await writeFile(join(folder, STARTED_MARKER), "");
        if (agentRun.answerFile)
            result.output = await readFile(agentRun.answerFile, "utf8").catch(() => result.output);

        const seconds = Math.round((Date.now() - startedAt) / 1000);
        log(`${request.cli} ended with ${describeEnd(result)} after ${seconds} s`);
        return result;
    } finally {
        for (const path of [...Object.keys(agentRun.files), paths.answer])
            await rm(path, { force: true });
        // A conversation that is never continued needs no folder.
        if (!request.resume) await rm(folder, { recursive: true, force: true });
    }
}

/** Stops the running CLI. Returns whether one was running. */
export function cancel(): boolean {
    if (!current.child) return false;

    current.cancelled = true;
    stopGroup(current.child);
    return true;
}

function spawnAgent(
    agentRun: AgentRun,
    folder: string,
    timeoutSeconds: number,
): Promise<RunResult> {
    const [executable = "", ...args] = agentRun.command;

    return new Promise((resolve) => {
        // A process group of its own, so cancelling also stops the processes the CLI starts.
        const child = spawn(executable, args, {
            cwd: folder,
            env: { ...process.env, ...agentRun.env },
            detached: true,
            stdio: ["ignore", "pipe", "pipe"],
        });
        current.child = child;
        current.cancelled = false;

        let output = "";
        let stderr = "";
        let timedOut = false;
        child.stdout
            .setEncoding("utf8")
            .on("data", (chunk: string) => (output = (output + chunk).slice(0, OUTPUT_LIMIT)));
        child.stderr
            .setEncoding("utf8")
            .on("data", (chunk: string) => (stderr = (stderr + chunk).slice(-STDERR_LIMIT)));

        const timer = setTimeout(() => {
            timedOut = true;
            stopGroup(child);
        }, timeoutSeconds * 1000);

        const finish = (exitCode: number | null, error?: Error) => {
            clearTimeout(timer);
            current.child = undefined;
            const stderrTail = (error ? `${stderr}\n${error.message}` : stderr)
                .trim()
                .slice(-STDERR_LIMIT);
            resolve({ exitCode, output, stderrTail, cancelled: current.cancelled, timedOut });
        };
        child.on("error", (error) => finish(null, error));
        child.on("close", (code) => finish(code));
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

function describeEnd({ exitCode, cancelled, timedOut }: RunResult): string {
    if (cancelled) return "a cancel";
    if (timedOut) return "a timeout";
    return `exit code ${exitCode}`;
}
