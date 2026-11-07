import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { catchUnwind, type AsyncResult } from "results-ts";
import type { McpServer } from "../agents.ts";
import {
    actionsToApprove,
    isRecord,
    readMarks,
    type Overrides,
    type ToolMarks,
} from "./tool-calls.ts";

// The real MCP server runs here, outside the container, with the token the agent never sees.
export interface McpHost {
    readonly server: McpServer;
    readonly env: NodeJS.ProcessEnv;
    readonly approvals: Approver;
}

export interface Approver {
    readonly overrides: Overrides;
    // Asks a player to approve the call. Fails with the reason for a denial.
    readonly ask: (request: ApprovalRequest) => AsyncResult<unknown, string>;
}

export interface ApprovalRequest {
    readonly tool: string;
    // As "tool.action".
    readonly actions: readonly string[];
    readonly arguments: Readonly<Record<string, unknown>>;
}

export interface McpConnection {
    // One JSON-RPC message from the agent.
    readonly receive: (line: string) => void;
    readonly end: () => void;
    readonly kill: () => void;
}

interface Message {
    readonly id?: string | number;
    readonly method?: string;
    readonly params?: { name?: unknown; arguments?: unknown };
    readonly result?: unknown;
}

// Each MCP connection gets its own server process, like a CLI that starts the server itself. Tool
// calls that need approval wait for it, and a denied call gets an error result instead of running.
export function connectMcp(
    mcp: McpHost,
    send: (line: string) => void,
    onClose: () => void,
): McpConnection {
    const server = spawn(mcp.server.command, [mcp.server.script], {
        env: mcp.env,
        stdio: ["pipe", "pipe", "ignore"],
    });
    const listRequests = new Set<string | number>();
    let marks = new Map<string, ToolMarks>();

    createInterface({ input: server.stdout }).on("line", (line) => {
        const message = parse(line);
        if (message?.id !== undefined && listRequests.delete(message.id)) {
            marks = new Map([...marks, ...readMarks(message.result)]);
        }
        send(line);
    });
    server.on("close", onClose);
    server.on("error", onClose);

    const forward = (line: string) => {
        if (server.stdin.writable) server.stdin.write(`${line}\n`);
    };

    return {
        receive: (line) => {
            const message = parse(line);
            if (message?.method === "tools/list" && message.id !== undefined) {
                listRequests.add(message.id);
            }
            if (message?.method !== "tools/call" || message.id === undefined) return forward(line);

            const tool = String(message.params?.name);
            const args = isRecord(message.params?.arguments) ? message.params.arguments : {};
            const actions = actionsToApprove(
                marks.get(tool),
                { tool, arguments: args },
                mcp.approvals.overrides,
            );
            if (actions.length === 0) return forward(line);

            const id = message.id;
            void mcp.approvals.ask({ tool, actions, arguments: args }).match({
                Ok: () => forward(line),
                Err: (reason) => send(JSON.stringify(deniedResult(id, reason))),
            });
        },
        end: () => server.stdin.end(),
        kill: () => server.kill(),
    };
}

function deniedResult(id: string | number, reason: string) {
    return {
        jsonrpc: "2.0",
        id,
        result: { content: [{ type: "text", text: reason }], isError: true },
    };
}

// Lines that are not JSON pass through for the other side to reject.
function parse(line: string): Message | undefined {
    const message = parseJson(line).unwrapOr(undefined);
    return isRecord(message) ? (message as Message) : undefined;
}

// Parsing reports failures only by throwing.
const parseJson = catchUnwind((line: string): unknown => JSON.parse(line));
