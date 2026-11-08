import { afterAll, beforeEach, describe, expect, test } from "bun:test";
import { rmSync } from "node:fs";
import { join } from "node:path";
import { fakes, home, resetFakes, until, untilAborted } from "./fakes.ts";
import { runRequest } from "./run-request.ts";

// The fakes replace the runner's modules only for imports that come after them.
const { cancel, runAgent } = await import("../src/runs.ts");

const request = runRequest();
const conversations = join(home, "conversations");

beforeEach(() => {
    resetFakes();
    rmSync(conversations, { recursive: true, force: true });
});
afterAll(() => rmSync(home, { recursive: true, force: true }));

describe("runs", () => {
    test("pass the request to the driver of its CLI", async () => {
        const result = await runAgent(runRequest({ cli: "codex" }));

        expect(result).toEqual({
            exitCode: 0,
            output: "Hello Alex",
            stderrTail: "",
            cancelled: false,
            timedOut: false,
        });
        expect(fakes.runs[0]?.executable).toBe("/opt/fake/bin/codex");
        expect(fakes.runs[0]?.folder).toBe(join(conversations, "player-1"));
    });

    test("never give the CLI the runner secret", async () => {
        await runAgent(request);

        expect(process.env.CONDUIT_RUNNER_SECRET).toBeDefined();
        expect(fakes.runs[0]?.env.CONDUIT_RUNNER_SECRET).toBeUndefined();
    });

    test("with an API key get a home without the logins", async () => {
        await runAgent(request);

        expect(fakes.runs[0]?.env.HOME).toBe(join(home, "api-key-home"));
    });

    test("with the subscription flag use the home that holds the logins", async () => {
        await runAgent(runRequest({ apiKey: "", dangerouslyUseSubscriptionCredentials: true }));

        expect(fakes.runs[0]?.env.HOME).toBe(home);
    });

    test("time out after the request's timeout", async () => {
        fakes.driver = untilAborted;

        const result = await runAgent(runRequest({ timeoutSeconds: 0.05 }));

        expect(result).toMatchObject({ exitCode: 1, timedOut: true, cancelled: false });
    });

    test("stop on cancel", async () => {
        fakes.driver = untilAborted;
        const running = runAgent(request);
        await until(() => fakes.runs.length === 1);

        expect(cancel()).toBe(true);
        expect(await running).toMatchObject({ exitCode: 1, cancelled: true, timedOut: false });
        expect(cancel()).toBe(false);
    });
});

describe("conversations", () => {
    test("continue the CLI's session", async () => {
        await runAgent(request);
        await runAgent(request);

        expect(fakes.runs.map((run) => run.sessionId)).toEqual([undefined, "session-1"]);
    });

    // Each run saves its own session, so every run differs from the one before in one way.
    test("start anew with another login store or CLI", async () => {
        const subscription = { apiKey: "", dangerouslyUseSubscriptionCredentials: true };
        await runAgent(request);
        await runAgent(runRequest(subscription));
        await runAgent(runRequest({ ...subscription, cli: "codex" }));

        expect(fakes.runs.map((run) => run.sessionId)).toEqual([undefined, undefined, undefined]);
    });
});

describe("errors", () => {
    test("leave out the API key, the MCP token and masked keys", async () => {
        fakes.driver = async () => {
            throw new Error(
                `401: key ${request.apiKey} refused, Bearer ${request.mcp.token}, ` +
                    "last tried sk-ant-api03-****wxyz.",
            );
        };

        const result = await runAgent(request);

        expect(result.stderrTail).toBe(
            "401: key [redacted] refused, Bearer [redacted], last tried [redacted]",
        );
        const logs = fakes.logs.join("\n");
        expect(logs).not.toContain(request.apiKey);
        expect(logs).not.toContain(request.mcp.token);
    });

    test("keep only their end", async () => {
        fakes.driver = async () => {
            throw new Error(`${"x".repeat(5000)}the end`);
        };

        const { stderrTail } = await runAgent(request);

        expect(stderrTail).toHaveLength(4000);
        expect(stderrTail.endsWith("the end")).toBe(true);
    });
});
