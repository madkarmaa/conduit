// The HTTP API that the mod calls. Every route needs the runner secret as a bearer token.
import { zValidator } from "@hono/zod-validator";
import { type Context, Hono } from "hono";
import { bearerAuth } from "hono/bearer-auth";
import { bodyLimit } from "hono/body-limit";
import { HTTPException } from "hono/http-exception";
import type { core } from "zod";
import {
    currentMaintenance,
    ensureInstalled,
    installed,
    installedClis,
    maintain,
    versions,
} from "./installs.ts";
import { log } from "./log.ts";
import { type Cli, RunRequest } from "./requests.ts";
import { cancel, type RunResult, runAgent } from "./runs.ts";

const BODY_LIMIT = 1_000_000;

// The run in progress. A cancel waits for it, so the next run finds the runner free.
let currentRun: Promise<RunResult> | undefined;
// The CLI of the last run, which the health check reports.
let lastCli: Cli | undefined = installedClis()[0];

export function isBusy(): boolean {
    return currentRun !== undefined;
}

export function createApp(secret: string): Hono {
    const app = new Hono();

    const limit = bodyLimit({
        maxSize: BODY_LIMIT,
        onError: (c) => c.json({ error: "The body is too large" }, 413),
    });

    const validRun = zValidator("json", RunRequest, (result, c) => {
        if (!result.success) return c.json({ error: describe(result.error) }, 400);
    });

    app.use(bearerAuth({ token: secret }));

    app.get("/health", (c) => c.json(health()));
    app.post("/cancel", async (c) => c.json({ cancelled: await cancelRun() }));
    app.post("/run", limit, validRun, (c) => startRun(c, c.req.valid("json")));

    app.notFound((c) => c.json({ error: "Unknown route" }, 404));
    app.onError((error, c) => {
        if (error instanceof HTTPException) return error.getResponse();
        log(`Request failed: ${error.stack ?? error}`);
        return c.json({ error: "The runner failed" }, 500);
    });

    return app;
}

async function startRun(c: Context, request: RunRequest): Promise<Response> {
    if (currentRun) {
        return c.json(
            { error: "The agent is still busy with another request, try again in a moment" },
            409,
        );
    }

    // Installing a CLI can take minutes, longer than the mod waits for an answer.
    if (!currentMaintenance() && !installed(request.cli)) {
        maintain(`installing ${request.cli}`, () => ensureInstalled(request.cli));
    }
    const maintenance = currentMaintenance();
    if (maintenance) {
        return c.json({ error: `The runner is ${maintenance}, try again in a few minutes` }, 503);
    }

    lastCli = request.cli;
    currentRun = runAgent(request);
    try {
        return c.json(await currentRun);
    } finally {
        currentRun = undefined;
    }
}

// The CLI can take a few seconds to stop, and the mod sends the next run as soon as this returns.
async function cancelRun(): Promise<boolean> {
    const cancelled = cancel();
    await currentRun?.catch(() => undefined);
    return cancelled;
}

function health() {
    return {
        cli: lastCli,
        version: lastCli ? versions[lastCli] : undefined,
        versions,
        busy: isBusy(),
        updating: currentMaintenance() !== undefined,
    };
}

// Names each field, such as "mcp.token: Invalid input: expected string, received undefined".
function describe(error: core.$ZodError): string {
    return error.issues
        .map((issue) => [issue.path.join("."), issue.message].filter(Boolean).join(": "))
        .join("; ");
}
