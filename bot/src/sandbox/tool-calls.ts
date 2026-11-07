// Decides which MCP tool calls need a player's approval, from the marks that the Conduit MCP server
// puts in each tool's _meta.

const ACTIONS_KEY = "top.madkarma.conduit/actions";
const DESTRUCTIVE_OPERATIONS_KEY = "top.madkarma.conduit/destructiveOperations";

export interface ToolMarks {
    // Each action's access: read, write or destructive.
    readonly actions: Readonly<Record<string, string>>;
    // Only the batch tool has these. A batch is destructive when it holds one of them.
    readonly destructiveOperations: readonly string[];
}

// Tools and actions that need approval besides the destructive ones, and those that never do, as
// "tool" or "tool.action". For batch, the actions are its operations, such as "batch.setBlock".
export interface Overrides {
    readonly add: readonly string[];
    readonly remove: readonly string[];
}

export interface ToolCall {
    readonly tool: string;
    readonly arguments: Readonly<Record<string, unknown>>;
}

// Reads the marks of every tool in a tools/list result.
export function readMarks(result: unknown): Map<string, ToolMarks> {
    const marks = new Map<string, ToolMarks>();
    const tools = (result as { tools?: unknown } | undefined)?.tools;
    if (!Array.isArray(tools)) return marks;

    for (const tool of tools as { name?: unknown; _meta?: Record<string, unknown> }[]) {
        if (typeof tool.name !== "string") continue;
        const actions = tool._meta?.[ACTIONS_KEY];
        const operations = tool._meta?.[DESTRUCTIVE_OPERATIONS_KEY];

        marks.set(tool.name, {
            actions: isRecord(actions) ? (actions as Record<string, string>) : {},
            destructiveOperations: Array.isArray(operations) ? operations.map(String) : [],
        });
    }

    return marks;
}

// Returns the actions of a call that need approval, as "tool.action", or none. A tool the server
// did not list has no marks, so the call needs approval to be safe.
export function actionsToApprove(
    marks: ToolMarks | undefined,
    call: ToolCall,
    overrides: Overrides,
): string[] {
    if (!marks) return [call.tool];

    const needsApproval = ({ action, destructive }: CalledAction) => {
        const matches = (entry: string) =>
            entry === call.tool || entry === `${call.tool}.${action}`;
        return (destructive || overrides.add.some(matches)) && !overrides.remove.some(matches);
    };

    return calledActions(marks, call)
        .filter(needsApproval)
        .map(({ action }) => `${call.tool}.${action}`);
}

interface CalledAction {
    readonly action: string;
    readonly destructive: boolean;
}

function calledActions(marks: ToolMarks, call: ToolCall): CalledAction[] {
    if (marks.destructiveOperations.length > 0) {
        const operations = Array.isArray(call.arguments.operations)
            ? call.arguments.operations
            : [];
        const names = operations.flatMap((operation) =>
            isRecord(operation) ? Object.keys(operation) : [],
        );

        return [...new Set(names)].map((action) => ({
            action,
            destructive: marks.destructiveOperations.includes(action),
        }));
    }

    // Tools with one action take no action argument.
    const names = Object.keys(marks.actions);
    const requested = call.arguments.action;
    const action =
        typeof requested === "string" ? requested : names.length === 1 ? names[0] : undefined;
    if (action === undefined) return [];

    return [{ action, destructive: marks.actions[action] === "destructive" }];
}

export function isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}
