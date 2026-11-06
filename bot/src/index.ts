#!/usr/bin/env node
import { resolve } from "node:path";
import { call, close, connect } from "@conduit/client";
import type { AsyncResult, Result } from "results-ts";
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

void loadConfig(configPath)
    .mapErr((error) => `Invalid config ${error.path}:\n${error.message}`)
    .andThen((config) =>
        installedMcpServer()
            .mapErr((message) => `Cannot find the Conduit MCP server: ${message}`)
            .map((mcp) => ({ config, mcp })),
    )
    .andThen((setup) => agentsFor(setup.config).map((agents) => ({ ...setup, agents })))
    .andThenAsync((setup) => prepareImage(setup.agents.full).map(() => setup))
    .match({
        Ok: ({ config, mcp, agents }) => start(config, mcp, agents),
        Err: (message) => {
            console.error(message);
            process.exitCode = 1;
        },
    });

// Chat mode can use another model or effort, but always the same agent CLI and image.
function agentsFor(config: Config): Result<Record<Mode, Agent>, string> {
    const { agent, model, effort, args } = config;
    const chat = { model: config.chat.model ?? model, effort: config.chat.effort ?? effort, args };

    return createAgent(agent, { model, effort, args })
        .andThen((full) => createAgent(agent, chat).map((chatAgent) => ({ full, chat: chatAgent })))
        .mapErr((message) => `Invalid agent: ${message}`);
}

function prepareImage({ image }: Agent): AsyncResult<undefined, string> {
    return checkDocker().andThenAsync(() =>
        ensureImage(image, () => log(`Building the agent image ${image.name}, this takes a while`)),
    );
}

function start(config: Config, mcp: McpServer, agents: Record<Mode, Agent>) {
    const agent = agents.full;
    const address = process.env.CONDUIT_ADDRESS || undefined;
    const token = process.env.CONDUIT_TOKEN || undefined;
    const clients = connect({ address, token });
    const files = homeFiles(agent.files, config.files);
    // The config's variables are expected, while the ones the copied config files read are optional.
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
        // Only the MCP server outside the container gets the token. Each request adds its mode's
        // tool policy.
        mcp: {
            server: mcp,
            env: {
                ...process.env,
                CONDUIT_ADDRESS: address ?? "",
                CONDUIT_TOKEN: token ?? "",
            },
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
