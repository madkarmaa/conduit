// Stops a CLI that the runner started in a process group of its own, with what it started.
import type { ChildProcess } from "node:child_process";

const KILL_GRACE_MS = 5000;

export function stopGroup(child: ChildProcess): void {
    const groupId = child.pid;
    if (groupId === undefined) return;

    const signal = (name: NodeJS.Signals) => {
        try {
            process.kill(-groupId, name);
        } catch {
            // The group already ended.
        }
    };

    signal("SIGTERM");
    setTimeout(() => signal("SIGKILL"), KILL_GRACE_MS).unref();
}
