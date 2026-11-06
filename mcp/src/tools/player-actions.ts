import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done } from "../format.ts";
import { call } from "../grpc.ts";
import { player, text, toText, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function playerActionTools({ player: players }: ConduitClients) {
    return [
        defineTool({
            name: "edit_player",
            title: "Edit player",
            description:
                "Changes an online player: game_mode, food (level 0-20 and saturation), experience " +
                "(levels and points within the level) or operator status.",
            input: {
                action: z.enum(["game_mode", "food", "experience", "operator"]),
                player,
                game_mode: z.enum(["survival", "creative", "adventure", "spectator"]).optional(),
                food_level: z.int().min(0).max(20).optional(),
                saturation: z.number().min(0).optional(),
                levels: z.int().min(0).optional(),
                points: z.int().min(0).optional(),
                operator: z.boolean().optional().describe("Required for operator."),
            },
            actions: {
                game_mode: {
                    access: "write",
                    run: (input) =>
                        required(input.game_mode, "game_mode").andThenAsync((gameMode) =>
                            call(players, "setGameMode", { player: input.player, gameMode }).map(
                                () => done(`Game mode set to ${gameMode}`),
                            ),
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
                        required(input.operator, "operator").andThenAsync((operator) =>
                            call(players, "setOperator", { player: input.player, operator }).map(
                                () =>
                                    done(operator ? "Player is now operator" : "Operator removed"),
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "notify_player",
            title: "Notify player",
            description:
                "Shows something to an online player: a chat or action bar message, a title with " +
                "subtitle, or a sound.",
            input: {
                action: z.enum(["message", "title", "sound"]),
                player,
                message: text.optional().describe("Message, or the title for title."),
                subtitle: text.optional(),
                action_bar: z.boolean().default(false).describe("Show message in the action bar."),
                sound: z
                    .string()
                    .optional()
                    .describe('Sound id, e.g. "minecraft:entity.player.levelup".'),
                position: vec3.optional().describe("Sound position; defaults to the player."),
                volume: z.number().min(0).default(1),
                pitch: z.number().min(0.5).max(2).default(1),
            },
            actions: {
                message: {
                    access: "write",
                    run: (input) =>
                        required(input.message, "message").andThenAsync((message) =>
                            call(players, "sendMessage", {
                                player: input.player,
                                message: toText(message),
                                overlay: input.action_bar,
                            }).map(() => done("Message sent")),
                        ),
                },
                title: {
                    access: "write",
                    run: (input) =>
                        call(players, "sendTitle", {
                            player: input.player,
                            title: input.message === undefined ? undefined : toText(input.message),
                            subtitle:
                                input.subtitle === undefined ? undefined : toText(input.subtitle),
                        }).map(() => done("Title shown")),
                },
                sound: {
                    access: "write",
                    run: (input) =>
                        required(input.sound, "sound").andThenAsync((sound) =>
                            call(players, "playSound", { ...input, sound }).map(() =>
                                done("Sound played"),
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "kick",
            title: "Kick player",
            description: "Disconnects an online player with a reason.",
            input: { player, reason: text.default("Kicked by an operator") },
            actions: {
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
    ];
}
