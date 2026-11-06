import type { ConduitClients } from "@conduit/client";
import { Err, Ok } from "results-ts";
import { z } from "zod";
import { call, invalidInput } from "../grpc.ts";
import { drawRegion, paletteFits, summarizeRegion } from "../region.ts";
import { blockPos, dimension } from "../schemas.ts";
import { defineTool } from "../tool.ts";

// Drawn regions print one character per block, so they stay small enough to read.
const MAX_DRAWN_VOLUME = 8192;

export function regionTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "get_region",
            title: "Read region",
            description:
                "Reads a cuboid region, corners inclusive. 'summary' counts each block state. " +
                `'draw' prints every y layer as a character grid, up to ${MAX_DRAWN_VOLUME} blocks.`,
            input: {
                dimension,
                from: blockPos,
                to: blockPos,
                detail: z.enum(["summary", "draw"]).default("summary"),
                loaded_only: z
                    .boolean()
                    .default(false)
                    .describe("Do not load chunks. Unloaded positions read as void air."),
            },
            actions: {
                read: {
                    access: "read",
                    run: (input) => {
                        const request = { ...input, loadedOnly: input.loaded_only };
                        const drawing = input.detail === "draw";
                        const tooLarge = drawing && volume(input.from, input.to) > MAX_DRAWN_VOLUME;
                        const checked = tooLarge
                            ? Err(invalidInput(`draw supports up to ${MAX_DRAWN_VOLUME} blocks`))
                            : Ok(request);
                        return checked
                            .andThenAsync((value) => call(world, "getBlocks", value))
                            .andThen((region) => {
                                if (!drawing) return Ok(summarizeRegion(region));
                                if (paletteFits(region)) return Ok(drawRegion(region));
                                return Err(invalidInput("Too many block states to draw"));
                            });
                    },
                },
            },
        }),
    ];
}

function volume(from: z.infer<typeof blockPos>, to: z.infer<typeof blockPos>): number {
    const side = (first: number, second: number) => Math.abs(first - second) + 1;
    return side(from.x, to.x) * side(from.y, to.y) * side(from.z, to.z);
}
