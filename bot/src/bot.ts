import type { CallError, ConduitClients } from "@conduit/client";
import { accessMode, toolPolicy, type Mode } from "./access.ts";
import type { Agent } from "./agents.ts";
import { findRequest, type ChatMessage } from "./chat.ts";
import type { Config } from "./config.ts";
import type { Conversation, Conversations, StateError } from "./conversations.ts";
import { buildPrompt } from "./prompt.ts";
import { say, showStatus, toChatText, type Speaker } from "./replies.ts";
import type { Approvals } from "./approvals.ts";
import { runInContainer, type Container, type RunError } from "./sandbox/container.ts";
import type { McpHost } from "./sandbox/mcp-proxy.ts";
import { log } from "./log.ts";
import { withLogins } from "./sandbox/logins.ts";

export interface Bot {
    readonly clients: ConduitClients;
    readonly config: Config;
    readonly agents: Readonly<Record<Mode, AgentRunner>>;
    readonly conversations: Conversations;
    // Every MCP setting except the approvals, which depend on the request.
    readonly mcp: Omit<McpHost, "approvals">;
    readonly approvals: Approvals;
}

// What a mode needs to run its agent.
export interface AgentRunner {
    readonly agent: Agent;
    // Every container setting except the home, which depends on the conversation.
    readonly container: Omit<Container, "home">;
    // Login files in the host's home, by their path relative to it.
    readonly logins: ReadonlyMap<string, string>;
}

interface Request {
    readonly sender: ChatMessage;
    readonly text: string;
    readonly mode: Mode;
    // The conversation it continues.
    readonly key: string;
}

export function handleChat(bot: Bot, sender: ChatMessage) {
    findRequest(sender.message, bot.config.keyword).inspect((text) => {
        void accessMode(bot.clients.player, bot.config, sender).match({
            Ok: (mode) => {
                if (!mode) return tell(bot, sender, "You are not allowed to ask me.");
                if (text.length === 0)
                    return tell(bot, sender, "Write your request after the mention.");

                queue(bot, { sender, text, mode, key: bot.conversations.key(sender, mode) });
            },
            Err: (error) =>
                log(`Could not check ${sender.player}'s access: ${describeCall(error)}`),
        });
    });
}

function queue(bot: Bot, request: Request) {
    const waiting = bot.conversations.enqueue(request.key, () => answer(bot, request));
    if (waiting) status(bot, request.sender, "Waiting for your previous request…");
}

function answer(bot: Bot, request: Request): PromiseLike<void> {
    const { sender, key } = request;
    log(`${sender.player} asks in ${request.mode} mode: ${request.text}`);
    status(bot, sender, "Thinking…");

    return bot.conversations
        .open(key)
        .andThenAsync((conversation) => ask(bot, conversation, request))
        .inspect(() => {
            void bot.conversations
                .markStarted(key)
                .inspectErr((error) =>
                    log(`Could not save the conversation state: ${error.message}`),
                );
        })
        .match({
            Ok: (reply) => {
                // Only full mode shares conversations, so only its answers are for everyone.
                const shared = request.mode === "full" && bot.config.memory === "shared";
                const text = toChatText(reply, bot.config.replies.maxLength);
                void say(speaker(bot), shared ? "everyone" : sender.playerUuid, text).inspectErr(
                    (error) => log(`Could not post the answer: ${describeCall(error)}`),
                );
            },
            Err: (error) => {
                log(`The agent failed for ${sender.player}: ${describeFailure(error)}`);
                tell(bot, sender, failureMessage(error));
            },
        });
}

function ask(bot: Bot, conversation: Conversation, request: Request) {
    const { agent, logins, ...runner } = bot.agents[request.mode];
    const prompt = buildPrompt(bot.config, request.sender, request.text, request.mode);
    const command = agent.command(prompt, conversation.started);
    const container = { ...runner.container, home: conversation.directory };
    const policy = toolPolicy(bot.config.tools, request.mode, request.sender);
    const mcp: McpHost = {
        ...bot.mcp,
        env: { ...bot.mcp.env, ...policy },
        approvals: {
            overrides: bot.config.approvals,
            ask: (approval) => bot.approvals.ask(request.sender, approval),
        },
    };

    return withLogins(conversation.directory, logins, agent.exportedLogins, (setup) =>
        runInContainer(container, mcp, { setup, command }),
    ).map((output) => output.trim() || "Done.");
}

function tell(bot: Bot, sender: ChatMessage, text: string) {
    void say(speaker(bot), sender.playerUuid, text).inspectErr((error) =>
        log(`Could not message ${sender.player}: ${describeCall(error)}`),
    );
}

function status(bot: Bot, sender: ChatMessage, text: string) {
    void showStatus(speaker(bot), sender.playerUuid, text);
}

function speaker(bot: Bot): Speaker {
    return { clients: bot.clients, name: bot.config.keyword };
}

function failureMessage(error: RunError | StateError): string {
    if (error.kind === "timeout") return `I ran out of time after ${error.seconds} seconds.`;
    return "Something went wrong. The bot's log has the details.";
}

function describeFailure(error: RunError | StateError): string {
    switch (error.kind) {
        case "spawn":
        case "state":
            return error.message;
        case "timeout":
            return `timed out after ${error.seconds} seconds`;
        case "exit":
            return `exited with code ${error.code}\n${error.stderr}`;
    }
}

export function describeCall(error: CallError): string {
    return error.kind === "grpc" ? `${error.status}: ${error.details}` : error.message;
}
