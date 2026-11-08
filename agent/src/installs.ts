// Installs the agent CLIs into the home volume and keeps them up to date.
import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { promisify } from "node:util";
import { installAntigravity, updateAntigravity } from "./antigravity-release.ts";
import { log } from "./log.ts";
import { Cli } from "./requests.ts";

const HOME = process.env.HOME ?? "/home/agent";
// Bun installs the CLIs here, and their launchers into the bin folder.
const BUN_INSTALL = process.env.BUN_INSTALL ?? join(HOME, ".bun");
const BIN = process.env.BUN_INSTALL_BIN ?? join(BUN_INSTALL, "bin");
const GLOBAL_PACKAGES = join(BUN_INSTALL, "install", "global", "node_modules");

const UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000;
const UPDATE_RETRY_MS = 60 * 1000;

const execute = promisify(execFile);

// Antigravity CLI is no npm package, so antigravity-release.ts installs it.
type NpmCli = Exclude<Cli, "antigravity">;

export const PACKAGES: Record<NpmCli, string> = {
    claude: "@anthropic-ai/claude-code",
    codex: "@openai/codex",
    opencode: "@opencode/cli",
};

/** The version of each installed CLI, as the CLI reports it. */
export const versions: Partial<Record<Cli, string>> = {};

// What the runner installs or updates right now, such as "installing claude".
let maintenance: string | undefined;

export function currentMaintenance(): string | undefined {
    return maintenance;
}

export function cliPath(cli: Cli): string {
    return join(BIN, cli);
}

export function installed(cli: Cli): boolean {
    return existsSync(cliPath(cli));
}

export function installedClis(): Cli[] {
    return Cli.options.filter(installed);
}

export async function ensureInstalled(cli: Cli): Promise<void> {
    if (installed(cli)) {
        versions[cli] ??= await versionOf(cli);
        return;
    }

    log(`Installing ${cli}`);
    if (cli === "antigravity") await installAntigravity(cliPath(cli));
    else await bunInstall(PACKAGES[cli]);
    versions[cli] = await versionOf(cli);
    log(`Installed ${cli} ${versions[cli]}`);
}

/** Runs an install or update in the background. Runs are refused meanwhile. */
export function maintain(description: string, task: () => Promise<void>): void {
    maintenance = description;
    task()
        .catch((error: Error) => log(`${description} failed: ${error.message}`))
        .finally(() => (maintenance = undefined));
}

/** Updates the installed CLIs now and once a day, but never during a run or an install. */
export function scheduleUpdates(isBusy: () => boolean, delay = 0): void {
    setTimeout(() => {
        if (isBusy() || maintenance) return scheduleUpdates(isBusy, UPDATE_RETRY_MS);

        maintain("updating", updateInstalled);
        scheduleUpdates(isBusy, UPDATE_INTERVAL_MS);
    }, delay).unref();
}

export async function versionOf(cli: Cli): Promise<string> {
    const { stdout } = await execute(cli, ["--version"]).catch(() => ({ stdout: "unknown" }));
    return stdout.trim().split("\n")[0] ?? "unknown";
}

async function updateInstalled(): Promise<void> {
    for (const cli of installedClis()) {
        try {
            if (cli === "antigravity") await updateAntigravity(cliPath(cli), await versionOf(cli));
            else await updatePackage(cli);

            versions[cli] = await versionOf(cli);
            log(`${cli} is at ${versions[cli]}`);
        } catch (error) {
            log(`Could not update ${cli}: ${(error as Error).message}`);
        }
    }
}

// Reinstalling the same version would download it again, and the CLI is missing meanwhile.
async function updatePackage(cli: NpmCli): Promise<void> {
    const latest = await latestVersion(cli);
    if (latest !== (await installedVersion(cli))) await bunInstall(`${PACKAGES[cli]}@${latest}`);
}

// Some CLIs set up their native binary in a postinstall script, which Bun only runs for trusted
// packages. The download cache would only take space in the home volume.
async function bunInstall(spec: string): Promise<void> {
    await execute("bun", ["add", "--global", "--trust", spec], { maxBuffer: 16 * 1024 * 1024 });
    await execute("bun", ["pm", "cache", "rm"]);
}

// The version Bun installed, from the package's own manifest.
async function installedVersion(cli: NpmCli): Promise<string> {
    const manifest = join(GLOBAL_PACKAGES, PACKAGES[cli], "package.json");
    return JSON.parse(await readFile(manifest, "utf8")).version;
}

async function latestVersion(cli: NpmCli): Promise<string> {
    const { stdout } = await execute("bun", ["info", PACKAGES[cli], "version"]);
    return stdout.trim();
}
