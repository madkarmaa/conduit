import { randomBytes } from "node:crypto";
import { call } from "@conduit/client";
import { Err, Ok, type AsyncResult, type Result } from "results-ts";
import type { ChatMessage, CustomClick } from "./chat.ts";
import { log } from "./log.ts";
import { post, type Speaker } from "./replies.ts";
import type { ApprovalRequest } from "./sandbox/mcp-proxy.ts";

// The click event id of the approval buttons.
const CLICK_ID = "conduit:approval";
const ARGUMENTS_LENGTH = 200;

type Decision = "approve" | "deny";

interface Pending {
    readonly asker: ChatMessage;
    readonly decide: (decision: Decision, player: string) => void;
}

interface Outcome {
    readonly decision: Decision | "timeout";
    // Who decided, if anyone did.
    readonly player: string;
}

// Asks for approval with [Approve] and [Deny] buttons in the chat, which the asking player and the
// online operators get. A click from either counts. Any modified client can send any click, so
// each prompt has a random nonce that only the players who got it know.
export class Approvals {
    private readonly pending = new Map<string, Pending>();

    constructor(
        private readonly speaker: Speaker,
        private readonly timeoutSeconds: number,
    ) {}

    ask(asker: ChatMessage, request: ApprovalRequest): AsyncResult<undefined, string> {
        const nonce = randomBytes(16).toString("hex");

        return Ok(nonce)
            .mapAsync(async () => {
                const audience = await this.audience(asker);
                const decision = this.waitForDecision(nonce, asker);
                for (const player of audience) this.prompt(player, nonce, asker, request);
                return decision;
            })
            .andThen(({ decision, player }): Result<undefined, string> => {
                const actions = request.actions.join(", ");
                if (decision === "timeout") {
                    log(`Nobody answered the approval for ${actions}`);
                    return Err(`Nobody approved this in ${this.timeoutSeconds} seconds.`);
                }

                log(`${player} chose ${decision} for ${actions}`);
                return decision === "approve" ? Ok(undefined) : Err(`${player} denied this.`);
            });
    }

    // Handles a click on an approval button. Other clicks are not the bot's.
    handleClick(click: CustomClick) {
        if (click.id !== CLICK_ID) return;

        const [decision, nonce = ""] = (click.payload ?? "").replace(/^"|"$/g, "").split(":");
        const pending = this.pending.get(nonce);
        if (!pending || (decision !== "approve" && decision !== "deny")) return;

        if (click.playerUuid === pending.asker.playerUuid) {
            pending.decide(decision, click.player);
            return;
        }

        void call(this.speaker.clients.player, "getPlayer", { player: click.playerUuid }).match({
            Ok: (info) => {
                if (info.operator) pending.decide(decision, click.player);
            },
            Err: () => undefined,
        });
    }

    private waitForDecision(nonce: string, asker: ChatMessage) {
        return new Promise<Outcome>((resolve) => {
            const finish = (decision: Outcome["decision"], player: string) => {
                clearTimeout(timeout);
                this.pending.delete(nonce);
                resolve({ decision, player });
            };
            const timeout = setTimeout(() => finish("timeout", ""), this.timeoutSeconds * 1000);
            this.pending.set(nonce, { asker, decide: finish });
        });
    }

    // The asker and every online operator, by UUID.
    private async audience(asker: ChatMessage): Promise<string[]> {
        const operators = await call(this.speaker.clients.player, "listPlayers", {}).match({
            Ok: (list) =>
                list.players
                    .filter((player) => player.operator)
                    .map((player) => player.entity?.uuid ?? ""),
            Err: () => [],
        });
        return [...new Set([asker.playerUuid, ...operators.filter((uuid) => uuid !== "")])];
    }

    private prompt(player: string, nonce: string, asker: ChatMessage, request: ApprovalRequest) {
        const details = JSON.stringify(request.arguments);
        const shown =
            details.length > ARGUMENTS_LENGTH ? `${details.slice(0, ARGUMENTS_LENGTH)}…` : details;

        const button = (decision: Decision, label: string, color: string, hover: string) => ({
            text: label,
            color,
            bold: true,
            click_event: { action: "custom", id: CLICK_ID, payload: `${decision}:${nonce}` },
            hover_event: { action: "show_text", value: hover },
        });

        void post(this.speaker, player, [
            { text: `${asker.player}'s request wants to use ${request.actions.join(", ")} ` },
            { text: shown, color: "gray" },
            { text: "\n" },
            button("approve", "[Approve]", "green", "Run it"),
            { text: " " },
            button("deny", "[Deny]", "red", "Do not run it"),
        ]).inspectErr(() => log(`Could not ask ${player} for approval`));
    }
}
