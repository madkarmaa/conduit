import { spawn } from "node:child_process";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";

export type RunError =
    | { readonly kind: "spawn"; readonly message: string }
    | { readonly kind: "timeout"; readonly seconds: number }
    | { readonly kind: "exit"; readonly code: number | null; readonly stderr: string };

export interface RunOptions {
    readonly cwd: string;
    readonly env: NodeJS.ProcessEnv;
    readonly timeoutSeconds: number;
}

interface Outcome {
    readonly spawnError: Error | undefined;
    readonly code: number | null;
    readonly timedOut: boolean;
    readonly stdout: string;
    readonly stderr: string;
}

// Only the end of stderr is kept for error reports, since agents can log a lot.
const STDERR_LIMIT = 4000;

// Runs a command to completion and returns its stdout.
export function run(
    command: readonly string[],
    options: RunOptions,
): AsyncResult<string, RunError> {
    return Ok(command)
        .mapAsync((parts) => new Promise<Outcome>((resolve) => start(parts, options, resolve)))
        .andThen((outcome) => toResult(outcome, options));
}

function start(
    command: readonly string[],
    options: RunOptions,
    finish: (outcome: Outcome) => void,
) {
    const [executable = "", ...args] = command;
    const child = spawn(executable, args, {
        cwd: options.cwd,
        env: options.env,
        stdio: ["ignore", "pipe", "pipe"],
    });

    let stdout = "";
    let stderr = "";
    let timedOut = false;
    let spawnError: Error | undefined;

    child.stdout.setEncoding("utf8").on("data", (chunk: string) => (stdout += chunk));
    child.stderr.setEncoding("utf8").on("data", (chunk: string) => {
        stderr = (stderr + chunk).slice(-STDERR_LIMIT);
    });

    const timer = setTimeout(() => {
        timedOut = true;
        child.kill("SIGTERM");
    }, options.timeoutSeconds * 1000);

    child.on("error", (error) => (spawnError = error));
    // close follows error when the command could not start, and always comes after the output.
    child.on("close", (code) => {
        clearTimeout(timer);
        finish({ spawnError, code, timedOut, stdout, stderr });
    });
}

function toResult(outcome: Outcome, options: RunOptions): Result<string, RunError> {
    if (outcome.spawnError) return Err({ kind: "spawn", message: outcome.spawnError.message });
    if (outcome.timedOut) return Err({ kind: "timeout", seconds: options.timeoutSeconds });
    if (outcome.code !== 0) {
        return Err({ kind: "exit", code: outcome.code, stderr: outcome.stderr.trim() });
    }

    return Ok(outcome.stdout);
}
