import type { CallError, ConduitClients } from "@conduit/client";
import { mayAsk } from "./access.ts";
import { fill, type AgentCommand } from "./agents.ts";
import { findRequest, type ChatMessage } from "./chat.ts";
import type { Config } from "./config.ts";
import type { Conversation, Conversations, StateError } from "./conversations.ts";
import { buildPrompt } from "./prompt.ts";
import { say, showStatus, toChatText, type Speaker } from "./replies.ts";
import { run, type RunError } from "./runner.ts";

export interface Bot {
    readonly clients: ConduitClients;
    readonly config: Config;
    readonly agent: AgentCommand;
    readonly conversations: Conversations;
    readonly env: NodeJS.ProcessEnv;
}

export function handleChat(bot: Bot, sender: ChatMessage) {
    findRequest(sender.message, bot.config.keyword).inspect((request) => {
        void mayAsk(bot.clients.player, bot.config, sender).match({
            Ok: (allowed) => {
                if (!allowed) return tell(bot, sender, "You are not allowed to ask me.");
                if (request.length === 0)
                    return tell(bot, sender, "Write your request after the mention.");

                queue(bot, sender, request);
            },
            Err: (error) =>
                log(`Could not check ${sender.player}'s access: ${describeCall(error)}`),
        });
    });
}

function queue(bot: Bot, sender: ChatMessage, request: string) {
    const key = bot.conversations.key(sender);
    const waiting = bot.conversations.enqueue(key, () => answer(bot, sender, request, key));

    if (waiting) status(bot, sender, "Waiting for your previous request…");
}

function answer(bot: Bot, sender: ChatMessage, request: string, key: string): PromiseLike<void> {
    log(`${sender.player} asks: ${request}`);
    status(bot, sender, "Thinking…");

    return bot.conversations
        .open(key)
        .andThenAsync((conversation) =>
            ask(bot, conversation, buildPrompt(bot.config, sender, request)),
        )
        .inspect(() => {
            void bot.conversations
                .markStarted(key)
                .inspectErr((error) =>
                    log(`Could not save the conversation state: ${error.message}`),
                );
        })
        .match({
            Ok: (reply) => {
                const audience = bot.config.memory === "shared" ? "everyone" : sender.playerUuid;
                void say(
                    speaker(bot),
                    audience,
                    toChatText(reply, bot.config.maxReplyLength),
                ).inspectErr((error) => log(`Could not post the answer: ${describeCall(error)}`));
            },
            Err: (error) => {
                log(`The agent failed for ${sender.player}: ${describeFailure(error)}`);
                tell(bot, sender, failureMessage(error));
            },
        });
}

function ask(bot: Bot, conversation: Conversation, prompt: string) {
    const template = (conversation.started && bot.agent.resume) || bot.agent.start;

    return run(
        template.map((part) => fill(part, { prompt })),
        { cwd: conversation.directory, env: bot.env, timeoutSeconds: bot.config.timeoutSeconds },
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

export function log(message: string) {
    console.error(`[${new Date().toISOString()}] ${message}`);
}
