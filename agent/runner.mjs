#!/usr/bin/env node
// Takes agent runs from the Conduit mod over HTTP and runs them one at a time with the agent CLI.
// The CLI is not part of the image: the runner installs it into the home volume on first use and
// updates it once a day while idle, so it survives the container being recreated.
//
//   node runner.mjs           serves the API
//   node runner.mjs install   installs a CLI and exits, for conduit-login
import { execFile, spawn } from "node:child_process";
import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { join } from "node:path";
import { promisify } from "node:util";
import { buildRun, PACKAGES } from "./presets.mjs";

const HOME = process.env.HOME ?? "/home/agent";
const NPM_PREFIX = process.env.NPM_CONFIG_PREFIX ?? join(HOME, ".npm-global");
const PORT = Number(process.env.CONDUIT_RUNNER_PORT ?? 8080);
const SECRET = process.env.CONDUIT_RUNNER_SECRET ?? "";

const CONVERSATIONS = join(HOME, "conversations");
// Written into a conversation's folder after its first successful run, so later runs continue it.
const STARTED_MARKER = ".conduit-started";
const CONVERSATION_NAME = /^[A-Za-z0-9_-]{1,100}$/;

const UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000;
const UPDATE_RETRY_MS = 60 * 1000;
const KILL_GRACE_MS = 5000;
const BODY_LIMIT = 1_000_000;
const OUTPUT_LIMIT = 1_000_000;
const STDERR_LIMIT = 4000;

const execute = promisify(execFile);

const state = {
    busy: false,
    // What the runner installs or updates right now, such as "installing claude". Runs wait.
    maintenance: undefined,
    // The CLI of the last run, which the health check reports.
    cli: undefined,
    versions: {},
    child: undefined,
    cancelled: false,
};

if (process.argv[2] === "install") {
    const cli = process.argv[3];
    if (!(cli in PACKAGES)) fail(`Usage: runner.mjs install ${Object.keys(PACKAGES).join("|")}`);
    await ensureInstalled(cli);
    process.exit(0);
}

if (SECRET.length < 16) fail("Set CONDUIT_RUNNER_SECRET to a secret of at least 16 characters");

for (const cli of Object.keys(PACKAGES).filter(installed)) {
    state.versions[cli] = await versionOf(cli);
    state.cli ??= cli;
}

createServer((request, response) => {
    handle(request, response).catch((error) => {
        log(`Request failed: ${error.stack ?? error}`);
        if (!response.headersSent) reply(response, 500, { error: "The runner failed" });
    });
}).listen(PORT, () => log(`Listening on port ${PORT}`));

scheduleUpdate(0);

async function handle(request, response) {
    if (!authorized(request.headers.authorization)) {
        return reply(response, 401, { error: "Missing or invalid bearer token" });
    }

    const route = `${request.method} ${request.url}`;
    if (route === "GET /health") return reply(response, 200, health());
    if (route === "POST /cancel") return reply(response, 200, { cancelled: cancel() });
    if (route !== "POST /run") return reply(response, 404, { error: "Unknown route" });

    const body = await readJson(request);
    const problem = body === undefined ? "The body is not JSON" : invalidRun(body);
    if (problem) return reply(response, 400, { error: problem });
    if (state.busy) return reply(response, 409, { error: "Another run is in progress" });

    // Installing a CLI can take minutes, longer than the mod waits for an answer.
    if (!state.maintenance && !installed(body.cli))
        maintain(`installing ${body.cli}`, () => ensureInstalled(body.cli));
    if (state.maintenance) {
        return reply(response, 503, {
            error: `The runner is ${state.maintenance}, try again in a few minutes`,
        });
    }

    state.busy = true;
    try {
        reply(response, 200, await runAgent(body));
    } finally {
        state.busy = false;
    }
}

// Hashing first makes both sides the same length, which timingSafeEqual needs.
function authorized(header) {
    const digest = (text) => createHash("sha256").update(text).digest();
    return timingSafeEqual(digest(header ?? ""), digest(`Bearer ${SECRET}`));
}

function health() {
    const cli = state.cli;
    const version = cli ? state.versions[cli] : undefined;
    const updating = state.maintenance !== undefined;
    return { cli, version, versions: state.versions, busy: state.busy, updating };
}

function invalidRun(body) {
    const isString = (value) => typeof value === "string" && value.length > 0;

    if (!(body.cli in PACKAGES)) return `cli must be one of ${Object.keys(PACKAGES).join(", ")}`;
    if (!isString(body.model)) return "model is required";
    if (!isString(body.effort)) return "effort is required";
    if (!isString(body.prompt)) return "prompt is required";
    if (!Array.isArray(body.args) || !body.args.every((arg) => typeof arg === "string")) {
        return "args must be a list of strings";
    }
    if (typeof body.web !== "boolean" || typeof body.resume !== "boolean") {
        return "web and resume must be true or false";
    }
    if (!CONVERSATION_NAME.test(body.conversation ?? ""))
        return "conversation must be a simple name";
    if (!isString(body.mcp?.url) || !isString(body.mcp?.token)) return "mcp needs url and token";
    if (!(body.timeoutSeconds > 0)) return "timeoutSeconds must be positive";
    return undefined;
}

async function runAgent(request) {
    await ensureInstalled(request.cli);
    state.cli = request.cli;

    const folder = join(CONVERSATIONS, request.conversation);
    await mkdir(folder, { recursive: true });

    const id = randomUUID();
    const paths = {
        mcpConfig: `/tmp/conduit-${id}-mcp.json`,
        answer: `/tmp/conduit-${id}-answer.txt`,
    };
    const resume = request.resume && existsSync(join(folder, STARTED_MARKER));
    const agentRun = buildRun({ ...request, resume }, paths);

    for (const [path, content] of Object.entries(agentRun.files)) {
        await writeFile(path, content, { mode: 0o600 });
    }

    log(`Running ${request.cli} in ${request.conversation}${resume ? ", continuing it" : ""}`);
    const startedAt = Date.now();
    try {
        const result = await spawnAgent(agentRun, folder, request.timeoutSeconds);
        if (result.exitCode === 0) await writeFile(join(folder, STARTED_MARKER), "");
        if (agentRun.answerFile)
            result.output = await readFile(agentRun.answerFile, "utf8").catch(() => result.output);

        log(
            `${request.cli} ended with ${describeEnd(result)} after ${Math.round((Date.now() - startedAt) / 1000)} s`,
        );
        return result;
    } finally {
        for (const path of [...Object.keys(agentRun.files), paths.answer])
            await rm(path, { force: true });
        // A conversation that is never continued needs no folder.
        if (!request.resume) await rm(folder, { recursive: true, force: true });
    }
}

function spawnAgent(agentRun, folder, timeoutSeconds) {
    const [executable, ...args] = agentRun.command;

    return new Promise((resolve) => {
        // A process group of its own, so cancelling also stops the processes the CLI starts.
        const child = spawn(executable, args, {
            cwd: folder,
            env: { ...process.env, ...agentRun.env },
            detached: true,
            stdio: ["ignore", "pipe", "pipe"],
        });
        state.child = child;
        state.cancelled = false;

        let output = "";
        let stderr = "";
        let timedOut = false;
        child.stdout
            .setEncoding("utf8")
            .on("data", (chunk) => (output = (output + chunk).slice(0, OUTPUT_LIMIT)));
        child.stderr
            .setEncoding("utf8")
            .on("data", (chunk) => (stderr = (stderr + chunk).slice(-STDERR_LIMIT)));

        const timer = setTimeout(() => {
            timedOut = true;
            stopGroup(child);
        }, timeoutSeconds * 1000);

        const finish = (exitCode, error) => {
            clearTimeout(timer);
            state.child = undefined;
            const stderrTail = (error ? `${stderr}\n${error.message}` : stderr)
                .trim()
                .slice(-STDERR_LIMIT);
            resolve({ exitCode, output, stderrTail, cancelled: state.cancelled, timedOut });
        };
        child.on("error", (error) => finish(null, error));
        child.on("close", (code) => finish(code));
    });
}

function cancel() {
    const child = state.child;
    if (!child) return false;

    state.cancelled = true;
    stopGroup(child);
    return true;
}

function stopGroup(child) {
    const signal = (name) => {
        try {
            process.kill(-child.pid, name);
        } catch {
            // The group already ended.
        }
    };

    signal("SIGTERM");
    setTimeout(() => signal("SIGKILL"), KILL_GRACE_MS).unref();
}

function installed(cli) {
    return existsSync(join(NPM_PREFIX, "bin", cli));
}

async function ensureInstalled(cli) {
    if (installed(cli)) {
        state.versions[cli] ??= await versionOf(cli);
        return;
    }

    log(`Installing ${PACKAGES[cli]}`);
    await npmInstall(PACKAGES[cli]);
    state.versions[cli] = await versionOf(cli);
    log(`Installed ${cli} ${state.versions[cli]}`);
}

// The download cache would only take space in the home volume.
async function npmInstall(spec) {
    await execute("npm", ["install", "--global", spec], { maxBuffer: 16 * 1024 * 1024 });
    await execute("npm", ["cache", "clean", "--force"]);
}

// The version npm installed, from the package's own manifest.
async function installedVersion(cli) {
    const manifest = join(NPM_PREFIX, "lib", "node_modules", PACKAGES[cli], "package.json");
    return JSON.parse(await readFile(manifest, "utf8")).version;
}

async function latestVersion(cli) {
    const { stdout } = await execute("npm", ["view", PACKAGES[cli], "version"]);
    return stdout.trim();
}

async function versionOf(cli) {
    const { stdout } = await execute(cli, ["--version"]).catch(() => ({ stdout: "unknown" }));
    return stdout.trim().split("\n")[0];
}

function maintain(description, task) {
    state.maintenance = description;
    task()
        .catch((error) => log(`${description} failed: ${error.message}`))
        .finally(() => (state.maintenance = undefined));
}

// Updates wait until no run and no install is in progress.
function scheduleUpdate(delay) {
    setTimeout(() => {
        if (state.busy || state.maintenance) return scheduleUpdate(UPDATE_RETRY_MS);

        maintain("updating", updateInstalled);
        scheduleUpdate(UPDATE_INTERVAL_MS);
    }, delay).unref();
}

async function updateInstalled() {
    for (const cli of Object.keys(PACKAGES)) {
        if (!installed(cli)) continue;

        try {
            // Reinstalling the same version would download it again, and the CLI is missing meanwhile.
            const latest = await latestVersion(cli);
            if (latest !== (await installedVersion(cli)))
                await npmInstall(`${PACKAGES[cli]}@${latest}`);
            state.versions[cli] = await versionOf(cli);
            log(`${cli} is at ${state.versions[cli]}`);
        } catch (error) {
            log(`Could not update ${cli}: ${error.message}`);
        }
    }
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

function describeEnd({ exitCode, cancelled, timedOut }) {
    if (cancelled) return "a cancel";
    if (timedOut) return "a timeout";
    return `exit code ${exitCode}`;
}

function log(message) {
    console.log(`${new Date().toISOString()} ${message}`);
}

function fail(message) {
    console.error(message);
    process.exit(1);
}
