import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { createInterface } from "node:readline";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";
import { connectMcp, type McpConnection, type McpHost } from "./mcp-proxy.ts";
import { PausableTimer } from "./timer.ts";
import { ASSETS_MOUNT, SUPERVISOR_PATH, type FromContainer, type ToContainer } from "./protocol.ts";

export type RunError =
    | { readonly kind: "spawn"; readonly message: string }
    | { readonly kind: "timeout"; readonly seconds: number }
    | { readonly kind: "exit"; readonly code: number | null; readonly stderr: string };

// The agent's home inside the container.
export const CONTAINER_HOME = "/home/agent";

export interface Container {
    readonly image: string;
    readonly offline: boolean;
    // Host directory mounted as the agent's home, where its CLI keeps the conversation.
    readonly home: string;
    // Variables for the agent. The bot hands their values to docker through its environment, not
    // its arguments, so they do not show up in the process list.
    readonly env: Readonly<Record<string, string>>;
    // The bot's built files, which include the supervisor and the relay.
    readonly assets: string;
    readonly timeoutSeconds: number;
}

interface Outcome {
    readonly exit: Extract<FromContainer, { type: "exit" }> | undefined;
    readonly spawnError: Error | undefined;
    readonly timedOut: boolean;
    readonly dockerStderr: string;
}

export interface AgentRun {
    // Commands that prepare the agent's home, run in order before the agent.
    readonly setup: readonly (readonly string[])[];
    readonly command: readonly string[];
}

// Runs an agent command in a new container and returns its stdout.
export function runInContainer(
    container: Container,
    mcp: McpHost,
    agentRun: AgentRun,
): AsyncResult<string, RunError> {
    return Ok(agentRun)
        .mapAsync(
            (value) => new Promise<Outcome>((resolve) => start(container, mcp, value, resolve)),
        )
        .andThen((outcome) => toResult(outcome, container));
}

function start(
    container: Container,
    mcp: McpHost,
    { setup, command }: AgentRun,
    finish: (outcome: Outcome) => void,
) {
    const name = `conduit-bot-${randomUUID()}`;
    const docker = spawn("docker", dockerArguments(container, name), {
        env: { ...process.env, ...container.env },
        stdio: ["pipe", "pipe", "pipe"],
    });

    const connections = new Map<number, McpConnection>();
    let exit: Outcome["exit"];
    let spawnError: Error | undefined;
    let timedOut = false;
    let dockerStderr = "";

    const send = (frame: ToContainer) => {
        if (docker.stdin.writable) docker.stdin.write(`${JSON.stringify(frame)}\n`);
    };

    const timer = new PausableTimer(container.timeoutSeconds * 1000, () => {
        timedOut = true;
        spawn("docker", ["kill", name], { stdio: "ignore" });
    });
    // The time a player takes to answer an approval does not count against the request.
    const host: McpHost = {
        ...mcp,
        approvals: {
            ...mcp.approvals,
            ask: (request) => {
                timer.pause();
                return mcp.approvals.ask(request).inspect(timer.resume).inspectErr(timer.resume);
            },
        },
    };

    const open = (id: number) =>
        connectMcp(
            host,
            (line) => send({ type: "mcp", id, line }),
            () => send({ type: "close", id }),
        );

    createInterface({ input: docker.stdout }).on("line", (line) => {
        const frame = JSON.parse(line) as FromContainer;
        if (frame.type === "open") connections.set(frame.id, open(frame.id));
        if (frame.type === "mcp") connections.get(frame.id)?.receive(frame.line);
        if (frame.type === "close") connections.get(frame.id)?.end();
        if (frame.type === "exit") exit = frame;
    });
    docker.stderr.setEncoding("utf8").on("data", (chunk: string) => (dockerStderr += chunk));

    docker.on("error", (error) => (spawnError = error));
    docker.on("close", () => {
        timer.stop();
        for (const connection of connections.values()) connection.kill();
        finish({ exit, spawnError, timedOut, dockerStderr });
    });

    send({ type: "start", setup, command });
}

function dockerArguments(container: Container, name: string): string[] {
    const user =
        process.getuid && process.getgid
            ? ["--user", `${process.getuid()}:${process.getgid()}`]
            : [];
    const env = Object.keys(container.env).flatMap((variable) => ["-e", variable]);

    return [
        ...["run", "--rm", "--interactive", "--name", name],
        // The agent may only change its own home and /tmp, and gains no privileges.
        ...["--read-only", "--tmpfs", "/tmp", "--cap-drop", "ALL"],
        ...["--security-opt", "no-new-privileges", "--pids-limit", "512"],
        ...["--network", container.offline ? "none" : "bridge"],
        // The agent can write files in its home that the bot's user can still clean up.
        ...user,
        ...["-e", `HOME=${CONTAINER_HOME}`, ...env],
        ...["-v", `${container.home}:${CONTAINER_HOME}`],
        ...["-v", `${container.assets}:${ASSETS_MOUNT}:ro`],
        ...["-w", CONTAINER_HOME, container.image],
        ...["node", SUPERVISOR_PATH],
    ];
}

function toResult(outcome: Outcome, container: Container): Result<string, RunError> {
    if (outcome.spawnError) return Err({ kind: "spawn", message: outcome.spawnError.message });
    if (outcome.timedOut) return Err({ kind: "timeout", seconds: container.timeoutSeconds });

    const { exit } = outcome;
    if (!exit) return Err({ kind: "exit", code: null, stderr: outcome.dockerStderr.trim() });
    if (exit.code !== 0) return Err({ kind: "exit", code: exit.code, stderr: exit.stderr });

    return Ok(exit.stdout);
}
