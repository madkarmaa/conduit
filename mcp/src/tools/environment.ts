import type { ConduitClients } from "conduit-client";
import { Err, Ok } from "results-ts";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call, invalidInput } from "../grpc.ts";
import { dimension } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

const WEATHER = {
    clear: "WEATHER_CLEAR",
    rain: "WEATHER_RAIN",
    thunder: "WEATHER_THUNDER",
} as const;

export function environmentTools({ world }: ConduitClients) {
    return [
        defineTool({
            name: "time",
            title: "Time of day",
            description:
                "Reads the game time, time of day and day count, or sets the time. Set exactly one " +
                "of time, add or preset. A day is 24000 ticks; 0 is sunrise, 6000 noon, 18000 midnight.",
            input: {
                action: z.enum(["get", "set"]),
                dimension,
                time: z.int().min(0).optional().describe("Absolute time of day in ticks."),
                add: z.int().optional().describe("Ticks to add to the current time."),
                preset: z.enum(["day", "noon", "night", "midnight"]).optional(),
            },
            actions: {
                get: { access: "read", run: (input) => call(world, "getTime", input).map(json) },
                set: {
                    access: "write",
                    run: (input) => {
                        const values = [input.time, input.add, input.preset].filter(
                            (value) => value !== undefined,
                        );
                        const checked =
                            values.length === 1
                                ? Ok(input)
                                : Err(invalidInput("Set exactly one of time, add or preset"));
                        return checked.andThenAsync((value) =>
                            call(world, "setTime", value).map(() => done("Time set")),
                        );
                    },
                },
            },
        }),
        defineTool({
            name: "weather",
            title: "Weather",
            description: "Reads or sets the weather.",
            input: {
                action: z.enum(["get", "set"]),
                dimension,
                weather: z
                    .enum(["clear", "rain", "thunder"])
                    .optional()
                    .describe("Required for set."),
                duration_ticks: z.int().min(0).default(0).describe("0 means a random duration."),
            },
            actions: {
                get: { access: "read", run: (input) => call(world, "getWeather", input).map(json) },
                set: {
                    access: "write",
                    run: (input) =>
                        required(input.weather, "weather").andThenAsync((weather) =>
                            call(world, "setWeather", {
                                weather: WEATHER[weather],
                                duration: input.duration_ticks,
                            }).map(() => done(`Weather set to ${weather}`)),
                        ),
                },
            },
        }),
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
                        required(input.name, "name")
                            .andThen((name) =>
                                required(input.value, "value").map((value) => ({ name, value })),
                            )
                            .andThenAsync((rule) =>
                                call(world, "setGameRule", rule).map(() =>
                                    done(`${rule.name} = ${rule.value}`),
                                ),
                            ),
                },
            },
        }),
    ];
}
