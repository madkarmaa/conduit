// Runs the agent for one request in its conversation's folder, through the SDK of its CLI.
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { runAntigravity } from "./drivers/antigravity.ts";
import { runClaude } from "./drivers/claude.ts";
import { runCodex } from "./drivers/codex.ts";
import type { DriverAnswer, DriverRun } from "./drivers/driver.ts";
import { runOpenCode } from "./drivers/opencode.ts";
import { cliPath, ensureInstalled } from "./installs.ts";
import { log } from "./log.ts";
import type { Cli, RunRequest } from "./requests.ts";

const HOME = process.env.HOME ?? "/home/agent";
const CONVERSATIONS = join(HOME, "conversations");
// Runs with an API key get a home without the logins, so no CLI can fall back to a subscription.
const API_KEY_HOME = join(HOME, "api-key-home");
// Written into a conversation's folder after a run, so the next run continues the CLI's session.
const SESSION_FILE = ".conduit-session.json";
const ERROR_LIMIT = 4000;

const DRIVERS: Record<Cli, (run: DriverRun) => Promise<DriverAnswer>> = {
    antigravity: runAntigravity,
    claude: runClaude,
    codex: runCodex,
    opencode: runOpenCode,
};

export type RunResult = {
    exitCode: number | null;
    output: string;
    stderrTail: string;
    cancelled: boolean;
    timedOut: boolean;
};

type Credentials = "API key" | "subscription";
type Session = { cli: Cli; credentials: Credentials; id: string };

let current: { abort: AbortController; cancelled: boolean; timedOut: boolean } | undefined;

export async function runAgent(request: RunRequest): Promise<RunResult> {
    await ensureInstalled(request.cli);

    const folder = join(CONVERSATIONS, request.conversation);
    const credentials = request.dangerouslyUseSubscriptionCredentials ? "subscription" : "API key";
    const home = credentials === "subscription" ? HOME : API_KEY_HOME;
    await mkdir(folder, { recursive: true });
    await mkdir(home, { recursive: true });
    const sessionId = await savedSession(folder, request.cli, credentials);

    const run = { abort: new AbortController(), cancelled: false, timedOut: false };
    current = run;
    const timer = setTimeout(() => {
        run.timedOut = true;
        run.abort.abort();
    }, request.timeoutSeconds * 1000);

    const continuing = sessionId ? ", continuing it" : "";
    log(`Running ${request.cli} in ${request.conversation} with its ${credentials}${continuing}`);
    const startedAt = Date.now();

    try {
        const answer = await DRIVERS[request.cli]({
            request,
            executable: cliPath(request.cli),
            folder,
            env: cliEnvironment(home),
            sessionId,
            abort: run.abort,
        });

        if (answer.sessionId) {
            await saveSession(folder, { cli: request.cli, credentials, id: answer.sessionId });
        }

        logEnd(request.cli, "an answer", startedAt);
        return {
            exitCode: 0,
            output: answer.output,
            stderrTail: "",
            cancelled: false,
            timedOut: false,
        };
    } catch (error) {
        logEnd(
            request.cli,
            run.cancelled ? "a cancel" : run.timedOut ? "a timeout" : "an error",
            startedAt,
        );

        const stderrTail = redact(String((error as Error).message ?? error), request);
        return { exitCode: 1, output: "", stderrTail, ...run };
    } finally {
        clearTimeout(timer);
        current = undefined;
    }
}

/** Stops the running agent. Returns whether one was running. */
export function cancel(): boolean {
    if (!current) return false;

    current.cancelled = true;
    current.abort.abort();
    return true;
}

// The CLI never needs the runner secret.
function cliEnvironment(home: string): Record<string, string> {
    const { CONDUIT_RUNNER_SECRET: _secret, ...inherited } = process.env;
    const defined = Object.entries(inherited).filter(
        (entry): entry is [string, string] => entry[1] !== undefined,
    );
    return { ...Object.fromEntries(defined), HOME: home };
}

// A session belongs to its CLI and to the login store it was made with.
async function savedSession(folder: string, cli: Cli, credentials: Credentials) {
    const text = await readFile(join(folder, SESSION_FILE), "utf8").catch(() => undefined);
    const saved = text ? (JSON.parse(text) as Session) : undefined;
    return saved?.cli === cli && saved.credentials === credentials ? saved.id : undefined;
}

async function saveSession(folder: string, session: Session): Promise<void> {
    await writeFile(join(folder, SESSION_FILE), JSON.stringify(session));
}

// Providers repeat keys in their errors, sometimes masked down to their ends.
function redact(text: string, { apiKey, mcp }: RunRequest): string {
    let clean = text;

    for (const secret of [apiKey, mcp.token]) {
        if (secret) clean = clean.replaceAll(secret, "[redacted]");
    }

    return clean.replace(/\S*\*{3,}\S*/g, "[redacted]").slice(-ERROR_LIMIT);
}

function logEnd(cli: Cli, end: string, startedAt: number): void {
    log(`${cli} ended with ${end} after ${Math.round((Date.now() - startedAt) / 1000)} s`);
}
