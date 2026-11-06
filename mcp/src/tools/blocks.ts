import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call } from "../grpc.ts";
import { blockPos, blockState, dimension, noUpdates, snbt } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function blockTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "get_block",
            title: "Get block",
            description: "Block state at a position, and whether it has a block entity.",
            input: { dimension, pos: blockPos },
            actions: {
                get: { access: "read", run: (input) => call(world, "getBlock", input).map(json) },
            },
        }),
        defineTool({
            name: "set_block",
            title: "Set block",
            description: "Places a block state, optionally with block entity SNBT appended.",
            input: { dimension, pos: blockPos, state: blockState, no_updates: noUpdates },
            actions: {
                set: {
                    access: "write",
                    run: (input) =>
                        call(world, "setBlock", { ...input, noUpdates: input.no_updates }).map(() =>
                            done(`Placed ${input.state}`),
                        ),
                },
            },
        }),
        defineTool({
            name: "set_blocks",
            title: "Set blocks",
            description: "Places many individual blocks in one server tick.",
            input: {
                dimension,
                changes: z.array(z.object({ pos: blockPos, state: blockState })).min(1),
                no_updates: noUpdates,
            },
            actions: {
                set: {
                    access: "write",
                    run: (input) =>
                        call(world, "setBlocks", { ...input, noUpdates: input.no_updates }).map(
                            (response) => done(`Changed ${response.changed} blocks`),
                        ),
                },
            },
        }),
        defineTool({
            name: "break_block",
            title: "Break block",
            description: "Breaks a block like a player would, optionally dropping its items.",
            input: { dimension, pos: blockPos, drop_items: z.boolean().default(true) },
            actions: {
                break: {
                    access: "write",
                    run: (input) =>
                        call(world, "breakBlock", { ...input, dropItems: input.drop_items }).map(
                            () => done("Block broken"),
                        ),
                },
            },
        }),
        defineTool({
            name: "block_entity",
            title: "Block entity data",
            description:
                "Reads the SNBT of a block entity (chest, sign, spawner, ...) or merges SNBT into it.",
            input: {
                action: z.enum(["get", "merge"]),
                dimension,
                pos: blockPos,
                snbt: snbt.optional().describe("SNBT to merge; required for merge."),
            },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        call(world, "getBlockEntity", input).map((nbt) => done(nbt.snbt)),
                },
                merge: {
                    access: "write",
                    run: (input) =>
                        required(input.snbt, "snbt").andThenAsync((value) =>
                            call(world, "setBlockEntity", { ...input, snbt: value }).map(() =>
                                done("Block entity updated"),
                            ),
                        ),
                },
            },
        }),
    ];
}
