import type { CallToolResult, McpServer } from "@modelcontextprotocol/server";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";
import * as z from "zod";
import { invalidInput, type ToolError } from "./grpc.ts";
import { checkPlayers, offers, redact, type ToolPolicy } from "./policy.ts";

export type Access = "read" | "write";

export interface Reply {
    readonly text: string;
    readonly data?: Record<string, unknown>;
}

export interface Action<Input> {
    readonly access: Access;
    readonly run: (input: Input) => AsyncResult<Reply, ToolError>;
}

// Tools with several actions declare an `action` enum in their input with the same keys, optionally
// with a default.
export interface Tool<Shape extends z.ZodRawShape> {
    readonly name: string;
    readonly title: string;
    readonly description: string;
    readonly input: Shape;
    readonly actions: Record<string, Action<z.infer<z.ZodObject<Shape>>>>;
    // Returns data about players without naming them in a player argument, so a server limited to
    // one player cannot offer it.
    readonly seesOtherPlayers?: boolean;
}

export type Registration = (server: McpServer, policy: ToolPolicy) => void;

export function defineTool<Shape extends z.ZodRawShape>(tool: Tool<Shape>): Registration {
    return (server, policy) => {
        if (!offers(policy, tool)) return;

        const allowed = Object.entries(tool.actions).filter(
            ([, action]) => !policy.readOnly || action.access === "read",
        );
        if (allowed.length === 0) return;

        const schema = z.object(tool.input);
        const actionNames = allowed.map(([name]) => name);
        const restricted = allowed.length < Object.keys(tool.actions).length;
        const advertised = restricted ? schema.extend({ action: z.enum(actionNames) }) : schema;

        server.registerTool(
            tool.name,
            {
                title: tool.title,
                description: tool.description,
                inputSchema: advertised,
                annotations: {
                    readOnlyHint: allowed.every(([, action]) => action.access === "read"),
                    // MCP treats every tool that writes as destructive unless this hint says
                    // otherwise. This server exists to control the game, so no tool asks for extra
                    // confirmation. Read-only mode limits what an agent can change instead.
                    destructiveHint: false,
                    openWorldHint: false,
                },
            },
            (args: unknown) =>
                parseInput(schema, args)
                    .andThen((input) => checkPlayers(input, policy.player).map(() => input))
                    .andThen((input) =>
                        selectAction(allowed, "action" in input ? input.action : undefined).map(
                            (action) => ({ action, input }),
                        ),
                    )
                    .andThenAsync(({ action, input }) => action.run(input))
                    .map((reply) => (policy.player.length > 0 ? redact(reply) : reply))
                    .match({ Ok: toolResult, Err: errorResult }),
        );
    };
}

type WithFields<Input, Field extends keyof Input> = Input & {
    [Key in Field]-?: Exclude<Input[Key], undefined>;
};

// Fields only some actions need are optional in the schema, so each action checks its own.
export function required<Input extends object, Field extends keyof Input & string>(
    input: Input,
    ...fields: Field[]
): Result<WithFields<Input, Field>, ToolError> {
    const missing = fields.filter((field) => input[field] === undefined);
    if (missing.length > 0) return Err(invalidInput(`Missing ${missing.join(", ")}`));

    // The check above guarantees every listed field is set.
    return Ok(input as WithFields<Input, Field>);
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
