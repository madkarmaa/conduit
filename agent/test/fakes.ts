// Stands in for the agent CLIs, their installations and the log, so tests never start a CLI,
// install a package or use real credentials. Test files import this before the runner's modules.
import { mock } from "bun:test";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { DriverAnswer, DriverRun } from "../src/drivers/driver.ts";

type Driver = (run: DriverRun) => Promise<DriverAnswer>;

// runs.ts reads HOME when it loads, so the conversations go to a fresh folder.
export const home = mkdtempSync(join(tmpdir(), "conduit-agent-test-"));
process.env.HOME = home;
process.env.CONDUIT_RUNNER_SECRET = "runner-secret-for-tests";

const answer: Driver = async () => ({ output: "Hello Alex", sessionId: "session-1" });

export const fakes = {
    driver: answer,
    install: async (): Promise<void> => {},
    installed: true,
    maintenance: undefined as string | undefined,
    /** Every run the drivers got, in order. */
    runs: [] as DriverRun[],
    logs: [] as string[],
};

export function resetFakes(): void {
    fakes.driver = answer;
    fakes.install = async () => {};
    fakes.installed = true;
    fakes.maintenance = undefined;
    fakes.runs = [];
    fakes.logs = [];
}

/** Runs until the run is aborted, then fails like the SDKs do. */
export const untilAborted: Driver = (run) =>
    new Promise((_, reject) => {
        run.abort.signal.addEventListener("abort", () => reject(new Error("Aborted")));
    });

/** Waits until a condition holds, such as a driver getting its run. */
export async function until(condition: () => boolean): Promise<void> {
    const deadline = Date.now() + 2000;
    while (!condition()) {
        if (Date.now() > deadline) throw new Error("The condition never held");
        await Bun.sleep(1);
    }
}

function drive(run: DriverRun): Promise<DriverAnswer> {
    fakes.runs.push(run);
    return fakes.driver(run);
}

mock.module("../src/drivers/antigravity.ts", () => ({ runAntigravity: drive }));
mock.module("../src/drivers/claude.ts", () => ({ runClaude: drive }));
mock.module("../src/drivers/codex.ts", () => ({ runCodex: drive }));
mock.module("../src/drivers/opencode.ts", () => ({ runOpenCode: drive }));

mock.module("../src/log.ts", () => ({
    log: (message: string) => fakes.logs.push(message),
    fail: (message: string) => {
        throw new Error(message);
    },
}));

mock.module("../src/installs.ts", () => ({
    versions: {},
    cliPath: (cli: string) => `/opt/fake/bin/${cli}`,
    currentMaintenance: () => fakes.maintenance,
    ensureInstalled: () => fakes.install(),
    installed: () => fakes.installed,
    installedClis: () => [],
    maintain: (description: string) => {
        fakes.maintenance = description;
    },
}));
