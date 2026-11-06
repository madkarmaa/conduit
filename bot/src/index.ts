#!/usr/bin/env node
import { resolve } from "node:path";
import { call, close, connect, connectionFromEnv, type ConnectionOptions } from "@conduit/client";
import type { AsyncResult } from "results-ts";
import type { Mode } from "./access.ts";
import { createAgent, type Agent, type McpServer } from "./agents.ts";
import { describeCall, handleChat, type Bot } from "./bot.ts";
import { log } from "./log.ts";
import { listenToChat } from "./chat.ts";
import { loadConfig, type Config } from "./config.ts";
import { Conversations } from "./conversations.ts";
import { installedMcpServer } from "./mcp-server.ts";
import { builtFiles, homeFiles, passedVariables, referencedVariables } from "./sandbox/host.ts";
import { checkDocker, ensureImage } from "./sandbox/images.ts";

const configPath = resolve(process.argv[2] ?? "conduit-bot.json");

interface Setup {
    readonly config: Config;
    readonly connection: ConnectionOptions;
    readonly mcp: McpServer;
    readonly agents: Record<Mode, Agent>;
}

void loadConfig(configPath)
    .mapErr((error) => `Invalid config ${error.path}:\n${error.message}`)
    .andThen((config) => connectionFromEnv().map((connection) => ({ config, connection })))
    .andThen((setup) =>
        installedMcpServer()
            .mapErr((message) => `Cannot find the Conduit MCP server: ${message}`)
            .map((mcp) => ({ ...setup, mcp, agents: agentsFor(setup.config) })),
    )
    .andThenAsync((setup) => prepareImage(setup.agents.full).map((): Setup => setup))
    .match({
        Ok: start,
        Err: (message) => {
            console.error(message);
            process.exitCode = 1;
        },
    });

// Chat mode can use another model or effort, but always the same agent CLI and image.
function agentsFor(config: Config): Record<Mode, Agent> {
    const { agent, model, effort, args } = config;
    const full = createAgent(agent, { model, effort, args });
    const chat = config.chat && createAgent(agent, { ...config.chat, args });
    return { full, chat: chat ?? full };
}

function prepareImage({ image }: Agent): AsyncResult<undefined, string> {
    return checkDocker().andThenAsync(() =>
        ensureImage(image, () => log(`Building the agent image ${image.name}, this takes a while`)),
    );
}

function start({ config, connection, mcp, agents }: Setup) {
    const agent = agents.full;
    const clients = connect(connection);
    const files = homeFiles(agent.files, config.files);
    // A variable that the config lists must be set. A variable that a copied config file reads is
    // optional.
    const referenced = referencedVariables(files.values()).filter((name) => name in process.env);
    const variables = [...new Set([...config.env, ...referenced])];

    const bot: Bot = {
        clients,
        config,
        agents,
        conversations: new Conversations(config.stateDirectory, config.memory),
        container: {
            image: agent.image.name,
            offline: config.offline,
            env: { ...passedVariables(variables), ...agent.env },
            assets: builtFiles(),
            timeoutSeconds: config.timeoutSeconds,
        },
        logins: files,
        // Only the MCP server outside the container gets the variables with the host and the token.
        // Each request adds its mode's tool policy.
        mcp: { server: mcp, env: process.env },
    };

    // A wrong host, port or token would otherwise only show up as a failing event stream.
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
