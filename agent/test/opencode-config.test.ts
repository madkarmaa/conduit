import { describe, expect, test } from "bun:test";
import { serverConfig } from "../src/drivers/opencode-config.ts";
import { runRequest } from "./run-request.ts";

const config = (changes = {}) =>
    serverConfig(
        runRequest({
            cli: "opencode",
            model: "deepseek/deepseek-chat",
            timeoutSeconds: 900,
            ...changes,
        }),
    );

describe("OpenCode config", () => {
    // The old layout takes the timeout only as a number, and OpenCode skips an entry it cannot read,
    // which leaves the agent without the game's tools.
    test("put Conduit's server under mcp.servers, with the run's timeout for tool calls", () => {
        const { mcp } = config();

        expect(Object.keys(mcp)).toEqual(["servers"]);
        expect(mcp.servers.conduit).toMatchObject({
            type: "remote",
            url: "http://minecraft:25585/mcp",
            headers: { Authorization: "Bearer mcp-token-for-tests" },
            timeout: { execution: 900_000 },
        });
    });

    test("allow only Conduit's tools without the web", () => {
        expect(config({ web: false }).permission).toEqual({ "*": "deny", "conduit_*": "allow" });
    });

    test("keep Conduit's MCP server over the options", () => {
        const { mcp } = config({ options: { mcp: { servers: {} } } });
        expect(mcp.servers.conduit.url).toBe("http://minecraft:25585/mcp");
    });

    test("give the API key to the model's provider", () => {
        expect(config({ apiKey: "sk-test" }).provider).toEqual({
            deepseek: { options: { apiKey: "sk-test" } },
        });
    });
});
