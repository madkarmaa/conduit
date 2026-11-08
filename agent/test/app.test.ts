import { afterAll, beforeEach, describe, expect, test } from "bun:test";
import { rmSync } from "node:fs";
import { fakes, home, resetFakes, until, untilAborted } from "./fakes.ts";
import { runRequest } from "./run-request.ts";

// The fakes replace the runner's modules only for imports that come after them.
const { createApp } = await import("../src/app.ts");

const SECRET = "runner-secret-0123456789";
const app = createApp(SECRET);

function send(path: string, init: RequestInit = {}, token = SECRET): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return Promise.resolve(app.request(path, { ...init, headers }));
}

function postRun(body: unknown): Promise<Response> {
    return send("/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: typeof body === "string" ? body : JSON.stringify(body),
    });
}

beforeEach(resetFakes);
afterAll(() => rmSync(home, { recursive: true, force: true }));

describe("auth", () => {
    test("refuses requests without the runner secret", async () => {
        const response = await Promise.resolve(app.request("/health"));

        expect(response.status).toBe(401);
    });

    test("refuses a wrong secret, even on unknown routes", async () => {
        expect((await send("/health", {}, "wrong-secret-0123456789")).status).toBe(401);
        expect((await send("/nope", {}, "wrong-secret-0123456789")).status).toBe(401);
    });
});

describe("routes", () => {
    test("answers unknown routes with 404", async () => {
        const response = await send("/nope");

        expect(response.status).toBe(404);
        expect(await response.json()).toEqual({ error: "Unknown route" });
    });

    test("runs the agent and answers with its output", async () => {
        const response = await postRun(runRequest());

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            exitCode: 0,
            output: "Hello Alex",
            stderrTail: "",
            cancelled: false,
            timedOut: false,
        });
        expect(fakes.runs).toHaveLength(1);
    });

    test("reports the CLI of the last run in the health check", async () => {
        await postRun(runRequest({ cli: "codex" }));

        const response = await send("/health");

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            cli: "codex",
            versions: {},
            busy: false,
            updating: false,
        });
    });

    test("cancel says when nothing runs", async () => {
        const response = await send("/cancel", { method: "POST" });

        expect(await response.json()).toEqual({ cancelled: false });
    });

    test("cancel stops the running agent and waits for it", async () => {
        fakes.driver = untilAborted;
        const running = postRun(runRequest());
        await until(() => fakes.runs.length === 1);

        expect(await (await send("/health")).json()).toMatchObject({ busy: true });
        expect(await (await send("/cancel", { method: "POST" })).json()).toEqual({
            cancelled: true,
        });
        expect(await (await running).json()).toMatchObject({ exitCode: 1, cancelled: true });
        expect(await (await send("/health")).json()).toMatchObject({ busy: false });
    });

    test("refuses a second run with 409 while one runs", async () => {
        fakes.driver = untilAborted;
        const running = postRun(runRequest());
        await until(() => fakes.runs.length === 1);

        const second = await postRun(runRequest({ prompt: "Steve asks: hi" }));

        expect(second.status).toBe(409);
        expect(await second.json()).toEqual({
            error: "The agent is still busy with another request, try again in a moment",
        });
        await send("/cancel", { method: "POST" });
        await running;
        expect(fakes.runs).toHaveLength(1);
    });
});

describe("maintenance", () => {
    test("refuses runs with 503 while the runner updates", async () => {
        fakes.maintenance = "updating";

        const response = await postRun(runRequest());

        expect(response.status).toBe(503);
        expect(await response.json()).toEqual({
            error: "The runner is updating, try again in a few minutes",
        });
        expect(fakes.runs).toHaveLength(0);
    });

    test("starts installing a missing CLI and asks to come back", async () => {
        fakes.installed = false;

        const response = await postRun(runRequest({ cli: "opencode" }));

        expect(response.status).toBe(503);
        expect(await response.json()).toEqual({
            error: "The runner is installing opencode, try again in a few minutes",
        });
        expect(fakes.maintenance).toBe("installing opencode");
    });
});

describe("request bodies", () => {
    test("refuses a body over the limit with 413", async () => {
        const response = await postRun(runRequest({ prompt: "x".repeat(1_000_001) }));

        expect(response.status).toBe(413);
        expect(await response.json()).toEqual({ error: "The body is too large" });
        expect(fakes.runs).toHaveLength(0);
    });

    test("names each invalid field", async () => {
        const response = await postRun({ ...runRequest(), cli: "gemini", mcp: { url: "x" } });

        expect(response.status).toBe(400);
        expect(await response.json()).toEqual({
            error:
                'cli: Invalid option: expected one of "antigravity"|"claude"|"codex"|"opencode"; ' +
                "mcp.token: Invalid input: expected string, received undefined",
        });
    });

    test("names the API key when neither it nor the subscription flag is set", async () => {
        const response = await postRun(runRequest({ apiKey: "" }));

        expect(response.status).toBe(400);
        expect(await response.json()).toEqual({
            error: "apiKey: is required unless dangerouslyUseSubscriptionCredentials is on",
        });
    });

    test("refuses a body that is not JSON", async () => {
        const response = await postRun("{");

        expect(response.status).toBe(400);
        expect(fakes.runs).toHaveLength(0);
    });
});

test("hides unexpected failures behind a 500", async () => {
    fakes.install = async () => {
        throw new Error("No space left in /home/agent");
    };

    const response = await postRun(runRequest());

    expect(response.status).toBe(500);
    expect(await response.json()).toEqual({ error: "The runner failed" });
    expect(fakes.logs.some((line) => line.startsWith("Request failed: "))).toBe(true);
});
