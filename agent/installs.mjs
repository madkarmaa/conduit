// Installs the agent CLIs into the home volume and keeps them up to date.
import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { promisify } from "node:util";
import { log } from "./log.mjs";
import { PACKAGES } from "./presets.mjs";

const HOME = process.env.HOME ?? "/home/agent";
const NPM_PREFIX = process.env.NPM_CONFIG_PREFIX ?? join(HOME, ".npm-global");

const UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000;
const UPDATE_RETRY_MS = 60 * 1000;

const execute = promisify(execFile);

/** The version of each installed CLI, as the CLI reports it. */
export const versions = {};

// What the runner installs or updates right now, such as "installing claude".
let maintenance;

export function currentMaintenance() {
    return maintenance;
}

export function installed(cli) {
    return existsSync(join(NPM_PREFIX, "bin", cli));
}

export async function ensureInstalled(cli) {
    if (installed(cli)) {
        versions[cli] ??= await versionOf(cli);
        return;
    }

    log(`Installing ${PACKAGES[cli]}`);
    await npmInstall(PACKAGES[cli]);
    versions[cli] = await versionOf(cli);
    log(`Installed ${cli} ${versions[cli]}`);
}

/** Runs an install or update in the background. Runs are refused meanwhile. */
export function maintain(description, task) {
    maintenance = description;
    task()
        .catch((error) => log(`${description} failed: ${error.message}`))
        .finally(() => (maintenance = undefined));
}

/** Updates the installed CLIs now and once a day, but never during a run or an install. */
export function scheduleUpdates(isBusy, delay = 0) {
    setTimeout(() => {
        if (isBusy() || maintenance) return scheduleUpdates(isBusy, UPDATE_RETRY_MS);

        maintain("updating", updateInstalled);
        scheduleUpdates(isBusy, UPDATE_INTERVAL_MS);
    }, delay).unref();
}

export async function versionOf(cli) {
    const { stdout } = await execute(cli, ["--version"]).catch(() => ({ stdout: "unknown" }));
    return stdout.trim().split("\n")[0];
}

async function updateInstalled() {
    for (const cli of Object.keys(PACKAGES).filter(installed)) {
        try {
            // Reinstalling the same version would download it again, and the CLI is missing meanwhile.
            const latest = await latestVersion(cli);
            if (latest !== (await installedVersion(cli)))
                await npmInstall(`${PACKAGES[cli]}@${latest}`);

            versions[cli] = await versionOf(cli);
            log(`${cli} is at ${versions[cli]}`);
        } catch (error) {
            log(`Could not update ${cli}: ${error.message}`);
        }
    }
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
