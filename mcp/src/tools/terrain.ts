import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call } from "../grpc.ts";
import { blockPos, dimension } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function terrainTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "list_dimensions",
            title: "List dimensions",
            description: "Dimensions with their height range, loaded chunks, entities and players.",
            input: {},
            actions: {
                list: { access: "read", run: () => call(world, "listDimensions", {}).map(json) },
            },
        }),
        defineTool({
            name: "get_biome",
            title: "Get biome",
            description: "Biome id at a position.",
            input: { dimension, pos: blockPos },
            actions: {
                get: {
                    access: "read",
                    run: (input) => call(world, "getBiome", input).map((biome) => done(biome.id)),
                },
            },
        }),
        defineTool({
            name: "get_height",
            title: "Get height",
            description: "Y of the first free block above the surface at a column.",
            input: {
                dimension,
                x: z.int(),
                z: z.int(),
                heightmap: z
                    .enum([
                        "WORLD_SURFACE",
                        "MOTION_BLOCKING",
                        "MOTION_BLOCKING_NO_LEAVES",
                        "OCEAN_FLOOR",
                    ])
                    .default("MOTION_BLOCKING"),
            },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        call(world, "getHeight", input).map((response) => done(`y=${response.y}`)),
                },
            },
        }),
        defineTool({
            name: "chunk",
            title: "Chunk",
            description:
                "Reads a chunk's status, or force-loads it so entities can be spawned or moved there.",
            input: {
                action: z.enum(["get", "set_forced"]),
                dimension,
                chunk_x: z.int(),
                chunk_z: z.int(),
                forced: z.boolean().optional().describe("Required for set_forced."),
            },
            actions: {
                get: {
                    access: "read",
                    run: (input) => call(world, "getChunk", chunkRequest(input)).map(json),
                },
                set_forced: {
                    access: "write",
                    run: (input) =>
                        required(input.forced, "forced").andThenAsync((forced) =>
                            call(world, "setChunkForced", { ...chunkRequest(input), forced }).map(
                                () => done(forced ? "Chunk force-loaded" : "Chunk released"),
                            ),
                        ),
                },
            },
        }),
    ];
}

function chunkRequest(input: { dimension?: string; chunk_x: number; chunk_z: number }) {
    return { dimension: input.dimension, chunkX: input.chunk_x, chunkZ: input.chunk_z };
}
