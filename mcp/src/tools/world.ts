import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call } from "../grpc.ts";
import { blockPos, dimension, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function worldTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "spawn",
            title: "World spawn",
            description: "Reads or sets the world spawn point.",
            input: {
                action: z.enum(["get", "set"]),
                dimension,
                pos: blockPos.optional().describe("Required for set."),
                yaw: z.number().default(0),
                pitch: z.number().default(0),
            },
            actions: {
                get: { access: "read", run: () => call(world, "getSpawn", {}).map(json) },
                set: {
                    access: "write",
                    run: (input) =>
                        required(input.pos, "pos").andThenAsync((pos) =>
                            call(world, "setSpawn", { ...input, pos }).map(() =>
                                done("Spawn point set"),
                            ),
                        ),
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
                    .describe("How the explosion affects blocks; NONE breaks no blocks."),
            },
            actions: {
                explode: {
                    access: "write",
                    run: (input) => call(world, "explode", input).map(() => done("Exploded")),
                },
            },
        }),

        defineTool({
            name: "world_border",
            title: "World border",
            description:
                "Reads or changes a dimension's world border. Fields left out stay unchanged.",
            input: {
                action: z.enum(["get", "set"]),
                dimension,
                center_x: z.number().optional(),
                center_z: z.number().optional(),
                size: z.number().min(1).optional().describe("Side length in blocks."),
                transition_ms: z
                    .int()
                    .min(0)
                    .default(0)
                    .describe("Time to grow or shrink to size; 0 changes it instantly."),
                damage_per_block: z.number().min(0).optional(),
                damage_safe_zone: z.number().min(0).optional(),
                warning_blocks: z.int().min(0).optional(),
                warning_time_ticks: z.int().min(0).optional(),
            },
            actions: {
                get: {
                    access: "read",
                    run: (input) => call(world, "getWorldBorder", input).map(json),
                },
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
    ];
}
