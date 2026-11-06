// Runs inside the agent's container. It starts the agent CLI and forwards the MCP connections that
// the relay opens to the bot, which serves them with the Conduit MCP server.
import { spawn } from "node:child_process";
import { createServer, type Socket } from "node:net";
import { createInterface } from "node:readline";
import { SOCKET_PATH, type FromContainer, type ToContainer } from "./protocol.ts";

// Only the end of stderr is kept for error reports, since agents can log a lot.
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
            return runAgent(frame.command);
        case "mcp":
            return connections.get(frame.id)?.write(`${frame.line}\n`);
        case "close":
            connections.get(frame.id)?.end();
            return connections.delete(frame.id);
    }
}

function runAgent(command: readonly string[]) {
    const [executable = "", ...args] = command;
    const agent = spawn(executable, args, { stdio: ["ignore", "pipe", "pipe"] });

    let stdout = "";
    let stderr = "";
    agent.stdout.setEncoding("utf8").on("data", (chunk: string) => (stdout += chunk));
    agent.stderr.setEncoding("utf8").on("data", (chunk: string) => {
        stderr = (stderr + chunk).slice(-STDERR_LIMIT);
    });

    agent.on("error", (error) => (stderr += `\n${error.message}`));
    agent.on("close", (code) => {
        send({ type: "exit", code, stdout, stderr: stderr.trim() }, () => process.exit(0));
    });
}
