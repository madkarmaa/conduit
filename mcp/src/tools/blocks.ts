import type { ConduitClients } from "@conduit/client";
import { Err, Ok } from "results-ts";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call, type ToolError } from "../grpc.ts";
import { blockPos, blockState, dimension, noUpdates, snbt } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function blockTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "get_block",
            title: "Get block",
            description:
                "Block state, biome and block entity SNBT (chest, sign, ...) at a position.",
            input: { dimension, pos: blockPos },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        call(world, "getBlock", input).andThenAsync((block) =>
                            call(world, "getBiome", input).andThenAsync((biome) =>
                                call(world, "getBlockEntity", input)
                                    .map((nbt) => nbt.snbt)
                                    .orElse(withoutBlockEntity)
                                    .map((blockEntity) =>
                                        json({ ...block, biome: biome.id, blockEntity }),
                                    ),
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "edit_blocks",
            title: "Edit blocks",
            description:
                "set places block states at positions in one tick. fill fills a cuboid, corners " +
                "inclusive. break breaks a block like a player. merge_nbt merges SNBT into a " +
                "block entity.",
            input: {
                action: z.enum(["set", "fill", "break", "merge_nbt"]),
                dimension,
                changes: z
                    .array(z.object({ pos: blockPos, state: blockState }))
                    .min(1)
                    .optional()
                    .describe("Required for set."),
                from: blockPos.optional().describe("Fill corner."),
                to: blockPos.optional().describe("Opposite fill corner."),
                state: blockState.optional().describe("Required for fill."),
                replace: z
                    .string()
                    .optional()
                    .describe("fill only replaces blocks matching this block id or state."),
                pos: blockPos.optional().describe("Required for break and merge_nbt."),
                drop_items: z.boolean().default(true).describe("break drops the block's items."),
                snbt: snbt.optional().describe("Required for merge_nbt."),
                no_updates: noUpdates,
            },
            actions: {
                set: {
                    access: "write",
                    run: (input) =>
                        required(input, "changes").andThenAsync((request) =>
                            call(world, "setBlocks", {
                                ...request,
                                noUpdates: input.no_updates,
                            }).map((response) => done(`Changed ${response.changed} blocks`)),
                        ),
                },
                fill: {
                    access: "write",
                    run: (input) =>
                        required(input, "from", "to", "state").andThenAsync((request) =>
                            call(world, "fill", { ...request, noUpdates: input.no_updates }).map(
                                (response) => done(`Changed ${response.changed} blocks`),
                            ),
                        ),
                },
                break: {
                    access: "write",
                    run: (input) =>
                        required(input, "pos").andThenAsync((request) =>
                            call(world, "breakBlock", {
                                ...request,
                                dropItems: input.drop_items,
                            }).map(() => done("Block broken")),
                        ),
                },
                merge_nbt: {
                    access: "write",
                    run: (input) =>
                        required(input, "pos", "snbt").andThenAsync((request) =>
                            call(world, "setBlockEntity", request).map(() =>
                                done("Block entity updated"),
                            ),
                        ),
                },
            },
        }),
    ];
}

// Most blocks have no block entity, which the server reports as NOT_FOUND.
function withoutBlockEntity(error: ToolError) {
    if (error.kind === "grpc" && error.status === "NOT_FOUND") return Ok(undefined);
    return Err(error);
}
