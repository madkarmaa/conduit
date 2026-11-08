// Installs Antigravity CLI, which is a native binary on Google's release server instead of an npm
// package. Each platform's manifest names the latest version, its archive and the archive's SHA-512.
import { execFile } from "node:child_process";
import { chmod, mkdir, rename, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { promisify } from "node:util";

const MANIFESTS = "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests";

const execute = promisify(execFile);

type Release = { version: string; url: string; sha512: string };

export async function installAntigravity(binary: string): Promise<void> {
    await installRelease(await latestRelease(), binary);
}

/** Installs the latest release unless the installed version already is it. */
export async function updateAntigravity(binary: string, installed: string): Promise<void> {
    const latest = await latestRelease();
    if (latest.version !== installed) await installRelease(latest, binary);
}

// The image is Debian, so the glibc builds fit.
async function latestRelease(): Promise<Release> {
    const architecture = process.arch === "arm64" ? "arm64" : "amd64";
    const response = await fetch(`${MANIFESTS}/linux_${architecture}.json`);
    if (!response.ok) throw new Error(`The Antigravity release server answered ${response.status}`);
    return (await response.json()) as Release;
}

// Downloads next to the binary, since /tmp is memory, and replaces the binary in one rename.
async function installRelease(release: Release, binary: string): Promise<void> {
    const staging = join(dirname(binary), ".antigravity-download");
    const archive = join(staging, "antigravity.tar.gz");
    await rm(staging, { recursive: true, force: true });
    await mkdir(staging, { recursive: true });

    try {
        const response = await fetch(release.url);
        if (!response.ok) throw new Error(`The Antigravity download answered ${response.status}`);
        await Bun.write(archive, response);
        if ((await sha512Of(archive)) !== release.sha512) {
            throw new Error("The Antigravity download does not match its checksum");
        }

        await execute("tar", ["-xzf", archive, "-C", staging, "antigravity"]);
        await chmod(join(staging, "antigravity"), 0o755);
        await rename(join(staging, "antigravity"), binary);
    } finally {
        await rm(staging, { recursive: true, force: true });
    }
}

async function sha512Of(file: string): Promise<string> {
    const hasher = new Bun.CryptoHasher("sha512");
    for await (const chunk of Bun.file(file).stream()) hasher.update(chunk);
    return hasher.digest("hex");
}
