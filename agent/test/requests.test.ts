import { describe, expect, test } from "bun:test";
import { Cli, RunRequest } from "../src/requests.ts";
import { runRequest } from "./run-request.ts";

function issues(request: unknown) {
    const result = RunRequest.safeParse(request);
    return result.success
        ? []
        : result.error.issues.map(({ path, message }) => ({ path, message }));
}

describe("RunRequest", () => {
    test("accepts a complete request", () => {
        expect(issues(runRequest())).toEqual([]);
    });

    test("needs an API key unless the subscription flag is on", () => {
        expect(issues(runRequest({ apiKey: "" }))).toEqual([
            {
                path: ["apiKey"],
                message: "is required unless dangerouslyUseSubscriptionCredentials is on",
            },
        ]);
        expect(
            issues(runRequest({ apiKey: "", dangerouslyUseSubscriptionCredentials: true })),
        ).toEqual([]);
    });

    test("takes only the CLIs the runner knows", () => {
        expect(Cli.options).toEqual(["antigravity", "claude", "codex", "opencode"]);
        expect(issues({ ...runRequest(), cli: "gemini" }).map((issue) => issue.path)).toEqual([
            ["cli"],
        ]);
    });

    test("keeps conversation names inside the conversations folder", () => {
        for (const conversation of ["player-1_chat", "x".repeat(100)]) {
            expect(issues(runRequest({ conversation }))).toEqual([]);
        }

        for (const conversation of [
            "../escape",
            "a/b",
            ".",
            "",
            "x".repeat(101),
            "name with space",
        ]) {
            expect(issues(runRequest({ conversation }))).toEqual([
                { path: ["conversation"], message: "must be a simple name" },
            ]);
        }
    });

    test("needs a positive timeout", () => {
        expect(issues(runRequest({ timeoutSeconds: 0.5 }))).toEqual([]);
        expect(issues(runRequest({ timeoutSeconds: 0 })).map((issue) => issue.path)).toEqual([
            ["timeoutSeconds"],
        ]);
    });

    test("needs a model, an effort, a prompt and an MCP token", () => {
        const request = { ...runRequest({ model: "", effort: "", prompt: "" }), mcp: { url: "x" } };

        expect(issues(request).map((issue) => issue.path.join("."))).toEqual([
            "model",
            "effort",
            "prompt",
            "mcp.token",
        ]);
    });
});
