import type { ConduitClients } from "@conduit/client";
import * as z from "zod";
import { done } from "../format.ts";
import { call } from "../grpc.ts";
import { player, text, toText, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function playerActionTools({ player: players, server }: ConduitClients) {
    return [
        defineTool({
            name: "edit_player",
            title: "Edit player",
            description:
                "Changes an online player: game_mode, food (level 0-20 and saturation), experience " +
                "(levels and points within the level), operator status, or kick disconnects them.",
            input: {
                action: z.enum(["game_mode", "food", "experience", "operator", "kick"]),
                player,
                game_mode: z.enum(["survival", "creative", "adventure", "spectator"]).optional(),
                food_level: z.int().min(0).max(20).optional(),
                saturation: z.number().min(0).optional(),
                levels: z.int().min(0).optional(),
                points: z.int().min(0).optional(),
                operator: z.boolean().optional().describe("Required for operator."),
                reason: text.default("Kicked by an operator").describe("Kick message."),
            },
            actions: {
                game_mode: {
                    access: "write",
                    run: (input) =>
                        required(input, "game_mode").andThenAsync((request) =>
                            call(players, "setGameMode", {
                                player: request.player,
                                gameMode: request.game_mode,
                            }).map(() => done(`Game mode set to ${request.game_mode}`)),
                        ),
                },
                food: {
                    access: "write",
                    run: (input) =>
                        call(players, "setFood", { ...input, foodLevel: input.food_level }).map(
                            () => done("Food updated"),
                        ),
                },
                experience: {
                    access: "write",
                    run: (input) =>
                        call(players, "setExperience", input).map(() => done("Experience updated")),
                },
                operator: {
                    access: "destructive",
                    run: (input) =>
                        required(input, "operator").andThenAsync((request) =>
                            call(players, "setOperator", request).map(() =>
                                done(
                                    request.operator
                                        ? "Player is now operator"
                                        : "Operator removed",
                                ),
                            ),
                        ),
                },
                kick: {
                    access: "destructive",
                    run: (input) =>
                        call(players, "kick", {
                            player: input.player,
                            reason: toText(input.reason),
                        }).map(() => done("Player kicked")),
                },
            },
        }),
        defineTool({
            name: "notify",
            title: "Notify players",
            description:
                "message sends a chat or action bar message to a player, or to everyone when no " +
                "player is given. title shows a title and subtitle to a player. sound plays a " +
                "sound to a player.",
            input: {
                action: z.enum(["message", "title", "sound"]),
                player: player.optional().describe("Required for title and sound."),
                message: text.optional().describe("Message, or the title for title."),
                subtitle: text.optional(),
                action_bar: z.boolean().default(false).describe("Show message in the action bar."),
                sound: z
                    .string()
                    .optional()
                    .describe('Sound id, e.g. "minecraft:entity.player.levelup".'),
                position: vec3.optional().describe("Sound position. Defaults to the player."),
                volume: z.number().min(0).default(1),
                pitch: z.number().min(0.5).max(2).default(1),
            },
            actions: {
                message: {
                    access: "write",
                    run: (input) =>
                        required(input, "message").andThenAsync((request) => {
                            const message = toText(request.message);
                            const overlay = request.action_bar;
                            const sent = request.player
                                ? call(players, "sendMessage", { ...request, message, overlay })
                                : call(server, "broadcast", { message, overlay });
                            return sent.map(() => done("Message sent"));
                        }),
                },
                title: {
                    access: "write",
                    run: (input) =>
                        required(input, "player").andThenAsync((request) =>
                            call(players, "sendTitle", {
                                player: request.player,
                                title:
                                    request.message === undefined
                                        ? undefined
                                        : toText(request.message),
                                subtitle:
                                    request.subtitle === undefined
                                        ? undefined
                                        : toText(request.subtitle),
                            }).map(() => done("Title shown")),
                        ),
                },
                sound: {
                    access: "write",
                    run: (input) =>
                        required(input, "player", "sound").andThenAsync((request) =>
                            call(players, "playSound", request).map(() => done("Sound played")),
                        ),
                },
            },
        }),
    ];
}
