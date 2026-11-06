import { spawn } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { catchUnwind, catchUnwindAsync, Err, Ok, type AsyncResult, type Result } from "results-ts";
import type { ExportedLogins } from "../agents.ts";
import type { StateError } from "../conversations.ts";
import { errorMessage, log } from "../log.ts";
import { CONTAINER_HOME } from "./container.ts";

// Where the bot writes exported logins, relative to the agent's home.
const EXPORTED_LOGINS_FILE = "exported-logins.json";

// Logins go into the agent's home for one run, and refreshed tokens come back to the host even if
// the run failed. Exported logins reach the agent through the setup commands that import them.
export function withLogins<E>(
    home: string,
    files: ReadonlyMap<string, string>,
    exported: ExportedLogins | undefined,
    run: (setup: string[][]) => AsyncResult<string, E>,
): AsyncResult<string, E | StateError> {
    const imported = `${CONTAINER_HOME}/${EXPORTED_LOGINS_FILE}`;

    return currentLogins(exported)
        .andThenAsync((json) =>
            copyLogins(home, files, json).map((copies) => ({
                copies,
                setup: json !== undefined && exported ? [exported.import(imported)] : [],
            })),
        )
        .andThenAsync(async ({ copies, setup }): Promise<Result<string, E>> => {
            const answer = await run(setup);
            await returnLogins(copies).match({
                Ok: (saved) => saved.forEach((file) => log(`Saved the refreshed login in ${file}`)),
                Err: (error) => log(`Could not save a refreshed login: ${error.message}`),
            });
            return answer;
        });
}

// Without the exported logins, the agent can still log in through its files and variables.
function currentLogins(logins: ExportedLogins | undefined): AsyncResult<string | undefined, never> {
    if (!logins) return Ok(undefined).mapAsync((value) => Promise.resolve(value));

    return exportLogins(logins.export)
        .map((json): string | undefined => json)
        .orElse((message): Result<string | undefined, never> => {
            log(`Could not export the agent's logins: ${message}`);
            return Ok(undefined);
        });
}

// A login file in the agent's home for one run, copied from the host's home or exported there.
export interface LoginCopy {
    // The host file, or none for exported logins, which never go back.
    readonly host: string | undefined;
    readonly copy: string;
    readonly content: string;
}

function stateError(thrown: unknown): StateError {
    return { kind: "state", message: errorMessage(thrown) };
}

// Node reports file system failures only by rejecting.
const copyLogins = catchUnwindAsync(
    async (
        home: string,
        files: ReadonlyMap<string, string>,
        exported: string | undefined,
    ): Promise<LoginCopy[]> => {
        const copies: LoginCopy[] = [];

        if (exported !== undefined) {
            const copy = join(home, EXPORTED_LOGINS_FILE);
            await writeFile(copy, exported, { mode: 0o600 });
            copies.push({ host: undefined, copy, content: exported });
        }

        for (const [relative, host] of files) {
            const copy = join(home, relative);
            const content = await readFile(host, "utf8");

            await mkdir(dirname(copy), { recursive: true });
            await writeFile(copy, content, { mode: 0o600 });

            copies.push({ host, copy, content });
        }

        return copies;
    },

    stateError,
);

// Deletes the copies after a run, so no login stays behind in the agent's home. A CLI that
// refreshed its token saved the new one in its copy. It goes back to the host only if the host file
// did not change in the meantime, and only as valid JSON, so a broken or outdated copy never
// replaces a working login. Returns the host files it updated.
const returnLogins = catchUnwindAsync(
    async (copies: readonly LoginCopy[]): Promise<string[]> => {
        const saved: string[] = [];

        for (const { host, copy, content } of copies) {
            const refreshed = await readFile(copy, "utf8");
            await rm(copy);

            if (host === undefined) continue;

            if (refreshed === content || parseJson(refreshed).isErr()) continue;
            if ((await readFile(host, "utf8")) !== content) continue;

            await writeFile(host, refreshed);
            saved.push(host);
        }

        return saved;
    },

    stateError,
);

const parseJson = catchUnwind((text: string): unknown => JSON.parse(text));

// Runs a command on the host that prints the logins as JSON.
function exportLogins(command: readonly string[]): AsyncResult<string, string> {
    const [executable = "", ...args] = command;

    return Ok(command)
        .mapAsync(
            () =>
                new Promise<{ code: number | null; stdout: string; stderr: string }>((resolve) => {
                    const child = spawn(executable, args, { stdio: ["ignore", "pipe", "pipe"] });

                    let stdout = "";
                    let stderr = "";
                    child.stdout
                        .setEncoding("utf8")
                        .on("data", (chunk: string) => (stdout += chunk));
                    child.stderr
                        .setEncoding("utf8")
                        .on("data", (chunk: string) => (stderr += chunk));

                    child.on("error", (error) => (stderr += error.message));
                    child.on("close", (code) => resolve({ code, stdout, stderr }));
                }),
        )
        .andThen(({ code, stdout, stderr }) =>
            code === 0 ? Ok(stdout) : Err(`${command.join(" ")} failed: ${stderr.trim()}`),
        );
}
