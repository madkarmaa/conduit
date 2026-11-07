#!/usr/bin/env node
// Takes agent runs from the Conduit mod over HTTP and runs them one at a time with the agent CLI.
// The CLI is not part of the image: the runner installs it into the home volume on first use and
// updates it once a day while idle, so it survives the container being recreated.
//
//   node runner.mjs           serves the API
//   node runner.mjs install   installs a CLI and exits, for conduit-login
import { createHash, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";
import {
    currentMaintenance,
    ensureInstalled,
    installed,
    maintain,
    scheduleUpdates,
    versionOf,
    versions,
} from "./installs.mjs";
import { fail, log } from "./log.mjs";
import { PACKAGES } from "./presets.mjs";
import { cancel, invalidRun, runAgent } from "./runs.mjs";

const PORT = Number(process.env.CONDUIT_RUNNER_PORT ?? 8080);
const SECRET = process.env.CONDUIT_RUNNER_SECRET ?? "";
const BODY_LIMIT = 1_000_000;

// The run in progress. A cancel waits for it, so the next run finds the runner free.
let currentRun;
// The CLI of the last run, which the health check reports.
let lastCli;

if (process.argv[2] === "install") {
    const cli = process.argv[3];
    if (!(cli in PACKAGES)) fail(`Usage: runner.mjs install ${Object.keys(PACKAGES).join("|")}`);
    await ensureInstalled(cli);
    process.exit(0);
}

if (SECRET.length < 16) fail("Set CONDUIT_RUNNER_SECRET to a secret of at least 16 characters");

for (const cli of Object.keys(PACKAGES).filter(installed)) {
    versions[cli] = await versionOf(cli);
    lastCli ??= cli;
}

createServer((request, response) => {
    handle(request, response).catch((error) => {
        log(`Request failed: ${error.stack ?? error}`);
        if (!response.headersSent) reply(response, 500, { error: "The runner failed" });
    });
}).listen(PORT, () => log(`Listening on port ${PORT}`));

scheduleUpdates(() => currentRun !== undefined);

async function handle(request, response) {
    if (!authorized(request.headers.authorization)) {
        return reply(response, 401, { error: "Missing or invalid bearer token" });
    }

    const route = `${request.method} ${request.url}`;
    if (route === "GET /health") return reply(response, 200, health());
    if (route === "POST /cancel") return reply(response, 200, { cancelled: await cancelRun() });
    if (route !== "POST /run") return reply(response, 404, { error: "Unknown route" });

    const body = await readJson(request);
    const problem = body === undefined ? "The body is not JSON" : invalidRun(body);
    if (problem) return reply(response, 400, { error: problem });
    if (currentRun) {
        return reply(response, 409, {
            error: "The agent is still busy with another request, try again in a moment",
        });
    }

    // Installing a CLI can take minutes, longer than the mod waits for an answer.
    if (!currentMaintenance() && !installed(body.cli)) {
        maintain(`installing ${body.cli}`, () => ensureInstalled(body.cli));
    }
    if (currentMaintenance()) {
        return reply(response, 503, {
            error: `The runner is ${currentMaintenance()}, try again in a few minutes`,
        });
    }

    lastCli = body.cli;
    currentRun = runAgent(body);
    try {
        reply(response, 200, await currentRun);
    } finally {
        currentRun = undefined;
    }
}

// The CLI can take a few seconds to stop, and the mod sends the next run as soon as this returns.
async function cancelRun() {
    const cancelled = cancel();
    await currentRun?.catch(() => {});
    return cancelled;
}

// Hashing first makes both sides the same length, which timingSafeEqual needs.
function authorized(header) {
    const digest = (text) => createHash("sha256").update(text).digest();
    return timingSafeEqual(digest(header ?? ""), digest(`Bearer ${SECRET}`));
}

function health() {
    const version = lastCli ? versions[lastCli] : undefined;
    return {
        cli: lastCli,
        version,
        versions,
        busy: currentRun !== undefined,
        updating: currentMaintenance() !== undefined,
    };
}

function readJson(request) {
    return new Promise((resolve) => {
        let body = "";
        request.setEncoding("utf8");
        request.on("data", (chunk) => {
            body += chunk;
            if (body.length > BODY_LIMIT) request.destroy();
        });
        request.on("end", () => {
            try {
                resolve(JSON.parse(body));
            } catch {
                resolve(undefined);
            }
        });
        request.on("error", () => resolve(undefined));
    });
}

function reply(response, status, body) {
    response.writeHead(status, { "content-type": "application/json" });
    response.end(JSON.stringify(body));
}
