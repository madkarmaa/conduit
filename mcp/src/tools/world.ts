import type { ConduitClients } from "conduit-client";
import { Err, Ok } from "results-ts";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call, invalidInput } from "../grpc.ts";
import { blockPos, dimension } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

const WEATHER = {
    clear: "WEATHER_CLEAR",
    rain: "WEATHER_RAIN",
    thunder: "WEATHER_THUNDER",
} as const;

const column = {
    x: z.int().optional().describe("Block x of the column; required for height and chunks."),
    z: z.int().optional().describe("Block z of the column; required for height and chunks."),
};

export function worldTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "terrain",
            title: "Terrain",
            description:
                "height: y of the first free block above the surface of a column. chunk: whether " +
                "the chunk containing a column is loaded or force-loaded, with its entity counts.",
            input: {
                action: z.enum(["height", "chunk"]),
                dimension,
                ...column,
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
                height: {
                    access: "read",
                    run: (input) =>
                        required(input, "x", "z").andThenAsync((request) =>
                            call(world, "getHeight", request).map(({ y }) => done(`y=${y}`)),
                        ),
                },
                chunk: {
                    access: "read",
                    run: (input) =>
                        required(input, "x", "z").andThenAsync((request) =>
                            call(world, "getChunk", chunkOf(request)).map(json),
                        ),
                },
            },
        }),
        defineTool({
            name: "world_info",
            title: "World info",
            description:
                "Time (a day is 24000 ticks; 0 is sunrise, 6000 noon), weather, world spawn and " +
                "world border of a dimension.",
            input: { dimension },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        call(world, "getTime", input).andThenAsync((time) =>
                            call(world, "getWeather", input).andThenAsync((weather) =>
                                call(world, "getSpawn", {}).andThenAsync((spawn) =>
                                    call(world, "getWorldBorder", input).map((worldBorder) =>
                                        json({ time, weather, spawn, worldBorder }),
                                    ),
                                ),
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "world_settings",
            title: "World settings",
            description:
                "time sets the time of day from exactly one of time, add or preset; weather sets " +
                "the weather; spawn sets the world spawn; force_chunk force-loads or releases the " +
                "chunk containing a column, so entities can be spawned or moved there.",
            input: {
                action: z.enum(["time", "weather", "spawn", "force_chunk"]),
                dimension,
                time: z.int().min(0).optional().describe("Absolute time of day in ticks."),
                add: z.int().optional().describe("Ticks to add to the current time."),
                preset: z.enum(["day", "noon", "night", "midnight"]).optional(),
                weather: z.enum(["clear", "rain", "thunder"]).optional(),
                duration_ticks: z.int().min(0).default(0).describe("0 means a random duration."),
                pos: blockPos.optional().describe("Required for spawn."),
                yaw: z.number().default(0),
                pitch: z.number().default(0),
                ...column,
                forced: z.boolean().optional().describe("Required for force_chunk."),
            },
            actions: {
                time: {
                    access: "write",
                    run: (input) => {
                        const values = [input.time, input.add, input.preset];
                        const given = values.filter((value) => value !== undefined).length;
                        const checked =
                            given === 1
                                ? Ok(input)
                                : Err(invalidInput("Set exactly one of time, add or preset"));
                        return checked.andThenAsync((request) =>
                            call(world, "setTime", request).map(() => done("Time set")),
                        );
                    },
                },
                weather: {
                    access: "write",
                    run: (input) =>
                        required(input, "weather").andThenAsync(({ weather }) =>
                            call(world, "setWeather", {
                                weather: WEATHER[weather],
                                duration: input.duration_ticks,
                            }).map(() => done(`Weather set to ${weather}`)),
                        ),
                },
                spawn: {
                    access: "write",
                    run: (input) =>
                        required(input, "pos").andThenAsync((request) =>
                            call(world, "setSpawn", request).map(() => done("Spawn point set")),
                        ),
                },
                force_chunk: {
                    access: "write",
                    run: (input) =>
                        required(input, "x", "z", "forced").andThenAsync((request) =>
                            call(world, "setChunkForced", {
                                ...chunkOf(request),
                                forced: request.forced,
                            }).map(() =>
                                done(request.forced ? "Chunk force-loaded" : "Chunk released"),
                            ),
                        ),
                },
            },
        }),
    ];
}

function chunkOf(column: { dimension?: string; x: number; z: number }) {
    return {
        dimension: column.dimension,
        chunkX: Math.floor(column.x / 16),
        chunkZ: Math.floor(column.z / 16),
    };
}
