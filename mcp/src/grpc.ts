import type { CallError } from "@conduit/client";

export { call, fromServiceError } from "@conduit/client";

export type ToolError = CallError;

export function invalidInput(message: string): ToolError {
    return { kind: "invalid_input", message };
}
