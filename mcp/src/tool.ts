import type { CallToolResult, McpServer, ToolAnnotations } from "@modelcontextprotocol/server";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";
import { z } from "zod";
import { invalidInput, type ToolError } from "./grpc.ts";

// Destructive actions are irreversible or privileged, so hosts ask before running them.
export type Access = "read" | "write" | "destructive";

export interface Reply {
    readonly text: string;
    readonly data?: Record<string, unknown>;
}

export interface Action<Input> {
    readonly access: Access;
    readonly run: (input: Input) => AsyncResult<Reply, ToolError>;
}

// Tools with several actions declare an `action` enum in their input with the same keys.
export interface Tool<Shape extends z.ZodRawShape> {
    readonly name: string;
    readonly title: string;
    readonly description: string;
    readonly input: Shape;
    readonly actions: Record<string, Action<z.infer<z.ZodObject<Shape>>>>;
}

export type Registration = (server: McpServer, readOnly: boolean) => void;

export function defineTool<Shape extends z.ZodRawShape>(tool: Tool<Shape>): Registration {
    return (server, readOnly) => {
        const allowed = Object.entries(tool.actions).filter(
            ([, action]) => !readOnly || action.access === "read",
        );
        if (allowed.length === 0) return;

        const schema = z.object(tool.input);
        const actionNames = allowed.map(([name]) => name);
        const advertised =
            "action" in tool.input ? schema.extend({ action: z.enum(actionNames) }) : schema;

        server.registerTool(
            tool.name,
            {
                title: tool.title,
                description: tool.description,
                inputSchema: advertised,
                annotations: annotations(allowed.map(([, action]) => action.access)),
            },
            (args: Record<string, unknown>) =>
                parseInput(schema, args)
                    .andThen((input) =>
                        selectAction(allowed, args.action).map((action) => ({ action, input })),
                    )
                    .andThenAsync(({ action, input }) => action.run(input))
                    .match({ Ok: toolResult, Err: errorResult }),
        );
    };
}

// Fields only some actions need are optional in the schema, so each action checks its own.
export function required<T>(value: T | undefined, field: string): Result<T, ToolError> {
    return value === undefined ? Err(invalidInput(`${field} is required`)) : Ok(value);
}

function parseInput<Shape extends z.ZodRawShape>(
    schema: z.ZodObject<Shape>,
    args: unknown,
): Result<z.infer<z.ZodObject<Shape>>, ToolError> {
    const parsed = schema.safeParse(args);
    return parsed.success ? Ok(parsed.data) : Err(invalidInput(z.prettifyError(parsed.error)));
}

function selectAction<Input>(
    allowed: [string, Action<Input>][],
    requested: unknown,
): Result<Action<Input>, ToolError> {
    const [first] = allowed;
    if (allowed.length === 1 && first) return Ok(first[1]);
    const match = allowed.find(([name]) => name === requested);
    return match ? Ok(match[1]) : Err(invalidInput(`Unknown action ${String(requested)}`));
}

function annotations(accesses: Access[]): ToolAnnotations {
    return {
        readOnlyHint: accesses.every((access) => access === "read"),
        destructiveHint: accesses.includes("destructive"),
        openWorldHint: false,
    };
}

function toolResult(reply: Reply): CallToolResult {
    return {
        content: [{ type: "text", text: reply.text }],
        ...(reply.data && { structuredContent: reply.data }),
    };
}

function errorResult(error: ToolError): CallToolResult {
    const text =
        error.kind === "grpc"
            ? `${error.status}: ${error.details}`
            : `Invalid input: ${error.message}`;
    return { content: [{ type: "text", text }], isError: true };
}
