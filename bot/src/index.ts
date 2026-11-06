#!/usr/bin/env node
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { call, close, connect } from "@conduit/client";
import type { AsyncResult, Result } from "results-ts";
import type { Mode } from "./access.ts";
import { agentCommand, type AgentCommand, type McpServer } from "./agents.ts";
import { describeCall, handleChat, log, type Bot } from "./bot.ts";
import { listenToChat } from "./chat.ts";
import { loadConfig, type Config } from "./config.ts";
import { Conversations } from "./conversations.ts";
import { bundledMcpServer } from "./mcp-server.ts";
import { checkDocker, ensureImage } from "./sandbox/images.ts";

const configPath = resolve(process.argv[2] ?? "conduit-bot.json");

void loadConfig(configPath)
    .mapErr((error) => `Invalid config ${error.path}:\n${error.message}`)
    .andThen((config) =>
        bundledMcpServer()
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
function agentsFor(config: Config): Result<Record<Mode, AgentCommand>, string> {
    const { model, effort } = config.agent;
    const chat = { model: config.chat.model ?? model, effort: config.chat.effort ?? effort };

    return agentCommand(config.agent, { model, effort })
        .andThen((full) =>
            agentCommand(config.agent, chat).map((chatAgent) => ({ full, chat: chatAgent })),
        )
        .mapErr((message) => `Invalid agent: ${message}`);
}

// A custom image is the user's to build, so only preset images are built here.
function prepareImage(agent: AgentCommand): AsyncResult<undefined, string> {
    const { image } = agent;
    if (typeof image === "string") return checkDocker();

    return checkDocker().andThenAsync(() =>
        ensureImage(image, () => log(`Building the agent image ${image.name}, this takes a while`)),
    );
}

function start(config: Config, mcp: McpServer, agents: Record<Mode, AgentCommand>) {
    const agent = agents.full;
    const address = process.env.CONDUIT_ADDRESS || undefined;
    const token = process.env.CONDUIT_TOKEN || undefined;
    const clients = connect({ address, token });

    if (!agent.resume && config.memory !== "none") {
        log("The agent command has no resume form, so every request starts a new conversation.");
    }

    const bot: Bot = {
        clients,
        config,
        agents,
        conversations: new Conversations(config.stateDirectory, config.memory),
        container: {
            image: typeof agent.image === "string" ? agent.image : agent.image.name,
            offline: config.offline,
            env: { ...passedVariables(config.env), ...agent.env },
            assets: sandboxAssets(),
            timeoutSeconds: config.timeoutSeconds,
        },
        logins: loginFiles([...agent.files, ...config.files]),
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

// Files in the host's home, by their path relative to it. Missing ones are left out, since an agent
// can log in through a variable instead.
function loginFiles(paths: readonly string[]): Map<string, string> {
    const files = new Map<string, string>();
    for (const path of paths) {
        const source = join(homedir(), path);
        if (existsSync(source)) files.set(path, source);
        else log(`Not copying ~/${path}, it does not exist`);
    }
    return files;
}

function passedVariables(names: readonly string[]): Record<string, string> {
    const variables: Record<string, string> = {};
    for (const name of names) {
        const value = process.env[name];
        if (value === undefined) log(`Not passing ${name}, it is not set`);
        else variables[name] = value;
    }
    return variables;
}

// The supervisor and relay are built next to the bot, or into dist when the bot runs from source.
function sandboxAssets(): string {
    const bundled = fileURLToPath(new URL("./sandbox", import.meta.url));
    if (existsSync(join(bundled, "supervisor.mjs"))) return bundled;
    return fileURLToPath(new URL("../dist/sandbox", import.meta.url));
}
