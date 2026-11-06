import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { catchUnwind, catchUnwindAsync } from "results-ts";
import type { StateError } from "../conversations.ts";

// A login file from the host's home, copied into the agent's home for one run.
export interface LoginCopy {
    readonly host: string;
    readonly copy: string;
    readonly content: string;
}

function stateError(thrown: unknown): StateError {
    return { kind: "state", message: thrown instanceof Error ? thrown.message : String(thrown) };
}

// Node reports file system failures only by rejecting.
export const copyLogins = catchUnwindAsync(
    async (home: string, files: ReadonlyMap<string, string>): Promise<LoginCopy[]> => {
        const copies: LoginCopy[] = [];
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
// replaces a working login. Returns the host files that were updated.
export const returnLogins = catchUnwindAsync(
    async (copies: readonly LoginCopy[]): Promise<string[]> => {
        const saved: string[] = [];
        for (const { host, copy, content } of copies) {
            const refreshed = await readFile(copy, "utf8");
            await rm(copy);
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
