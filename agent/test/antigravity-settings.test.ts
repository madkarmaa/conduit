import { describe, expect, test } from "bun:test";
import { allowsTool, decision } from "../src/drivers/antigravity-hook.ts";
import {
    hooks,
    resumableSession,
    sessionKey,
    sessionMeta,
} from "../src/drivers/antigravity-settings.ts";
import { runRequest } from "./run-request.ts";

const request = (changes = {}) => runRequest({ cli: "antigravity", ...changes });

describe("Antigravity CLI tool hook", () => {
    test("allow Conduit's tools", () => {
        expect(allowsTool("mcp_conduit_world_info", false)).toBe(true);
    });

    test("deny commands, files, questions and other servers", () => {
        for (const tool of [
            "run_command",
            "read_file",
            "write_file",
            "ask_question",
            "mcp_other_x",
        ]) {
            expect(allowsTool(tool, true)).toBe(false);
        }
    });

    test("allow web search only with the web", () => {
        expect(allowsTool("search_web", false)).toBe(false);
        expect(allowsTool("search_web", true)).toBe(true);
    });

    test("give the agent a reason for a denied tool", () => {
        expect(decision("run_command", false)).toMatchObject({ decision: "deny" });
        expect(decision("run_command", false).reason).toBeString();
    });

    test("run before every tool, with the web flag only with the web", () => {
        const command = (web: boolean) =>
            hooks(request({ web })).conduit.PreToolUse[0]!.hooks[0]!.command;

        expect(hooks(request()).conduit.PreToolUse[0]!.matcher).toBe("*");
        expect(command(true)).toEndWith("antigravity-hook.ts --web");
        expect(command(false)).toEndWith("antigravity-hook.ts");
    });

    test("leave the token out", () => {
        expect(JSON.stringify(hooks(request()))).not.toContain("mcp-token-for-tests");
    });
});

describe("Antigravity CLI session options", () => {
    test("append the instructions to the system prompt", () => {
        expect(sessionMeta(request()).antigravity.systemPrompt).toEqual({
            append: "Answer in a few words.",
        });
    });
});

describe("Antigravity CLI sessions", () => {
    test("resume a session with the same instructions", () => {
        const saved = sessionKey("session-1", "Answer briefly.");
        expect(resumableSession(saved, "Answer briefly.")).toBe("session-1");
    });

    test("start over when the instructions changed", () => {
        const saved = sessionKey("session-1", "Answer briefly.");
        expect(resumableSession(saved, "Answer in detail.")).toBeUndefined();
    });

    test("start over without a saved session", () => {
        expect(resumableSession(undefined, "Answer briefly.")).toBeUndefined();
    });
});
