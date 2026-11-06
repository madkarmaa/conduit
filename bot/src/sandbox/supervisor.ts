// Runs inside the agent's container. It starts the agent CLI and forwards the MCP connections that
// the relay opens to the bot, which serves them with the Conduit MCP server.
import { spawn } from "node:child_process";
import { createServer, type Socket } from "node:net";
import { createInterface } from "node:readline";
import { SOCKET_PATH, type FromContainer, type ToContainer } from "./protocol.ts";

// The supervisor keeps only the end of stderr for error reports, since agents can log a lot.
const STDERR_LIMIT = 4000;

const connections = new Map<number, Socket>();
let nextId = 1;

function send(frame: FromContainer, then?: () => void) {
    process.stdout.write(`${JSON.stringify(frame)}\n`, then);
}

const server = createServer((socket) => {
    const id = nextId++;
    connections.set(id, socket);
    send({ type: "open", id });

    createInterface({ input: socket }).on("line", (line) => send({ type: "mcp", id, line }));
    socket.on("error", () => socket.destroy());
    socket.on("close", () => {
        if (connections.delete(id)) send({ type: "close", id });
    });
});

server.listen(SOCKET_PATH, () => {
    createInterface({ input: process.stdin }).on("line", (line) => {
        receive(JSON.parse(line) as ToContainer);
    });

    // The bot is gone, so nobody would read the answer.
    process.stdin.on("end", () => process.exit(1));
});

function receive(frame: ToContainer) {
    switch (frame.type) {
        case "start":
            return start(frame.setup, frame.command);
        case "mcp":
            return connections.get(frame.id)?.write(`${frame.line}\n`);
        case "close":
            connections.get(frame.id)?.end();
            return connections.delete(frame.id);
    }
}

// A failed setup command ends the run like a failed agent, with its own error.
function start(setup: readonly (readonly string[])[], command: readonly string[]) {
    const [step, ...rest] = setup;
    if (!step) return run(command, finish);

    run(step, (code, stdout, stderr) => {
        if (code === 0) start(rest, command);
        else finish(code, stdout, stderr);
    });
}

function finish(code: number | null, stdout: string, stderr: string) {
    send({ type: "exit", code, stdout, stderr }, () => process.exit(0));
}

function run(
    command: readonly string[],
    done: (code: number | null, stdout: string, stderr: string) => void,
) {
    const [executable = "", ...args] = command;
    const child = spawn(executable, args, { stdio: ["ignore", "pipe", "pipe"] });

    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8").on("data", (chunk: string) => (stdout += chunk));
    child.stderr.setEncoding("utf8").on("data", (chunk: string) => {
        stderr = (stderr + chunk).slice(-STDERR_LIMIT);
    });

    child.on("error", (error) => (stderr += `\n${error.message}`));
    child.on("close", (code) => done(code, stdout, stderr.trim()));
}
