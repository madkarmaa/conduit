import type { ConduitClients } from "@conduit/client";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call } from "../grpc.ts";
import { dimension, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function environmentTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "game_rules",
            title: "Game rules",
            description: "Lists every game rule with its value, or sets one.",
            input: {
                action: z.enum(["list", "set"]),
                name: z.string().optional().describe('Rule name, e.g. "advance_time".'),
                value: z.string().optional().describe('New value, e.g. "false" or "3".'),
            },
            actions: {
                list: {
                    access: "read",
                    run: () => call(world, "getGameRules", {}).map((rules) => json(rules.rules)),
                },
                set: {
                    access: "write",
                    run: (input) =>
                        required(input, "name", "value").andThenAsync((rule) =>
                            call(world, "setGameRule", rule).map(() =>
                                done(`${rule.name} = ${rule.value}`),
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "world_border",
            title: "Set world border",
            description:
                "Changes a dimension's world border. Fields left out stay unchanged. world_info " +
                "reads the current border.",
            input: {
                dimension,
                center_x: z.number().optional(),
                center_z: z.number().optional(),
                size: z.number().min(1).optional().describe("Side length in blocks."),
                transition_ms: z
                    .int()
                    .min(0)
                    .default(0)
                    .describe("Time to grow or shrink to size. 0 changes it instantly."),
                damage_per_block: z.number().min(0).optional(),
                damage_safe_zone: z.number().min(0).optional(),
                warning_blocks: z.int().min(0).optional(),
                warning_time_ticks: z.int().min(0).optional(),
            },
            actions: {
                set: {
                    access: "write",
                    run: (input) =>
                        call(world, "setWorldBorder", {
                            dimension: input.dimension,
                            centerX: input.center_x,
                            centerZ: input.center_z,
                            size: input.size,
                            transitionMs: input.transition_ms,
                            damagePerBlock: input.damage_per_block,
                            damageSafeZone: input.damage_safe_zone,
                            warningBlocks: input.warning_blocks,
                            warningTimeTicks: input.warning_time_ticks,
                        }).map(() => done("World border updated")),
                },
            },
        }),
        defineTool({
            name: "explode",
            title: "Explode",
            description: "Creates an explosion. TNT has power 4, a creeper 3.",
            input: {
                dimension,
                position: vec3,
                power: z.number().min(0).default(4),
                fire: z.boolean().default(false),
                interaction: z
                    .enum(["NONE", "BLOCK", "MOB", "TNT", "TRIGGER"])
                    .default("TNT")
                    .describe("How the explosion affects blocks. NONE breaks no blocks."),
            },
            actions: {
                explode: {
                    access: "write",
                    run: (input) => call(world, "explode", input).map(() => done("Exploded")),
                },
            },
        }),
    ];
}
