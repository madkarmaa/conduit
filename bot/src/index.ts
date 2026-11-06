#!/usr/bin/env node
import { resolve } from "node:path";
import { call, close, connect } from "@conduit/client";
import { agentCommand, type McpServer } from "./agents.ts";
import { describeCall, handleChat, log, type Bot } from "./bot.ts";
import { listenToChat } from "./chat.ts";
import { loadConfig, type Config } from "./config.ts";
import { Conversations } from "./conversations.ts";
import { bundledMcpServer } from "./mcp-server.ts";

const configPath = resolve(process.argv[2] ?? "conduit-bot.json");

loadConfig(configPath)
    .mapErr((error) => `Invalid config ${error.path}:\n${error.message}`)
    .andThen((config) =>
        bundledMcpServer()
            .mapErr((message) => `Cannot find the Conduit MCP server: ${message}`)
            .map((mcp) => ({ config, mcp })),
    )
    .match({
        Ok: ({ config, mcp }) => start(config, mcp),
        Err: (message) => {
            console.error(message);
            process.exitCode = 1;
        },
    });

function start(config: Config, mcp: McpServer) {
    const address = process.env.CONDUIT_ADDRESS || undefined;
    const token = process.env.CONDUIT_TOKEN || undefined;
    const clients = connect({ address, token });

    const agent = agentCommand(config.agent, mcp);
    if (!agent.resume && config.memory !== "none") {
        log("The agent command has no resume form, so every request starts a new conversation.");
    }

    const bot: Bot = {
        clients,
        config,
        agent,
        conversations: new Conversations(config.stateDirectory, config.memory),
        env: {
            ...process.env,
            ...agent.env,
            CONDUIT_ADDRESS: address ?? "",
            CONDUIT_TOKEN: token ?? "",
            CONDUIT_MCP_READ_ONLY: String(config.readOnly),
        },
    };

    // A wrong address or token would otherwise only show up as a failing event stream.
    void call(clients.server, "getInfo", {}).match({
        Ok: (info) => log(`Connected to Minecraft ${info.minecraftVersion}`),
        Err: (error) => log(`Cannot reach the server yet: ${describeCall(error)}`),
    });

    const listener = listenToChat(
        clients.events,
        (message) => handleChat(bot, message),
        (reason) => log(`Lost the chat stream (${reason}), reconnecting`),
    );
    log(`Listening for @${config.keyword} in the chat`);

    for (const signal of ["SIGINT", "SIGTERM"] as const) {
        process.once(signal, () => {
            listener.stop();
            close(clients);
        });
    }
}
