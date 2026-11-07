#!/usr/bin/env node
import { resolve } from "node:path";
import { call, close, connect, connectionFromEnv, type ConnectionOptions } from "@conduit/client";
import type { AsyncResult } from "results-ts";
import type { Mode } from "./access.ts";
import { createAgent, type Agent, type McpServer } from "./agents.ts";
import { Approvals } from "./approvals.ts";
import { describeCall, handleChat, type AgentRunner, type Bot } from "./bot.ts";
import { log } from "./log.ts";
import { listenToChat } from "./chat.ts";
import { chatAgent, loadConfig, type Config } from "./config.ts";
import { Conversations } from "./conversations.ts";
import { installedMcpServer } from "./mcp-server.ts";
import { RequestQueue } from "./queue.ts";
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
    .andThenAsync((setup) => prepareImages(setup.agents).map((): Setup => setup))
    .match({
        Ok: start,
        Err: (message) => {
            console.error(message);
            process.exitCode = 1;
        },
    });

// Chat mode can use another agent CLI, model or effort.
function agentsFor(config: Config): Record<Mode, Agent> {
    const full = createAgent(config.agents.full);
    const chat = chatAgent(config);
    return { full, chat: chat ? createAgent(chat) : full };
}

// Both modes can share one agent and its image.
function prepareImages(agents: Record<Mode, Agent>): AsyncResult<undefined, string> {
    const images = new Map(Object.values(agents).map(({ image }) => [image.name, image]));

    return [...images.values()].reduce(
        (previous, image) =>
            previous.andThenAsync(() =>
                ensureImage(image, () =>
                    log(`Building the agent image ${image.name}, this takes a while`),
                ),
            ),
        checkDocker(),
    );
}

function start({ config, connection, mcp, agents }: Setup) {
    const clients = connect(connection);

    const bot: Bot = {
        clients,
        config,
        agents: { full: runnerFor(agents.full, config), chat: runnerFor(agents.chat, config) },
        conversations: new Conversations(config.stateDirectory, config.memory),
        queue: new RequestQueue(),
        approvals: new Approvals(
            { clients, name: config.keyword },
            config.approvals.timeoutSeconds,
        ),
        // Only the MCP server outside the container gets the variables with the host and the token.
        // Each request adds its mode's tool policy.
        mcp: { server: mcp, env: process.env },
    };

    // A wrong host, port or token would otherwise only show up as a failing event stream.
    void call(clients.server, "getInfo", {}).match({
        Ok: (info) => log(`Connected to Minecraft ${info.minecraftVersion}`),
        Err: (error) => log(`Cannot reach the server yet: ${describeCall(error)}`),
    });

    const listener = listenToChat(clients.events, {
        onMessage: (message) => handleChat(bot, message),
        onClick: (click) => bot.approvals.handleClick(click),
        onDisconnect: (reason) => log(`Lost the chat stream (${reason}), reconnecting`),
    });
    log(`Listening for @${config.keyword} in the chat`);

    for (const signal of ["SIGINT", "SIGTERM"] as const) {
        process.once(signal, () => {
            listener.stop();
            close(clients);
        });
    }
}

function runnerFor(agent: Agent, config: Config): AgentRunner {
    const { sandbox } = config;
    const logins = homeFiles(agent.files, sandbox.files);
    // A copied config file can read variables, which the agent then needs too, if they are set.
    const referenced = referencedVariables(logins.values()).filter((name) => name in process.env);

    return {
        agent,
        logins,
        container: {
            image: agent.image.name,
            offline: sandbox.offline,
            env: { ...passedVariables(referenced), ...sandbox.env, ...agent.env },
            assets: builtFiles(),
            timeoutSeconds: sandbox.timeoutSeconds,
        },
    };
}
