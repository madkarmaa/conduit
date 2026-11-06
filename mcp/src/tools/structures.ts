import type { ConduitClients } from "@conduit/client";
import * as z from "zod";
import { done, json, lines } from "../format.ts";
import { call } from "../grpc.ts";
import { blockPos, dimension, limit } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

const ROTATIONS = {
    none: "STRUCTURE_ROTATION_NONE",
    clockwise_90: "STRUCTURE_ROTATION_CLOCKWISE_90",
    clockwise_180: "STRUCTURE_ROTATION_CLOCKWISE_180",
    counterclockwise_90: "STRUCTURE_ROTATION_COUNTERCLOCKWISE_90",
} as const;

const MIRRORS = {
    none: "STRUCTURE_MIRROR_NONE",
    left_right: "STRUCTURE_MIRROR_LEFT_RIGHT",
    front_back: "STRUCTURE_MIRROR_FRONT_BACK",
} as const;

export function structureTools({ structure }: ConduitClients) {
    return [
        defineTool({
            name: "structures",
            title: "Structures",
            description:
                "Structure templates, the same ones structure blocks and /place template use. list " +
                "finds saved and datapack templates, including vanilla ones such as " +
                '"minecraft:village/plains/houses/plains_small_house_1". save copies a region into ' +
                "a template. place pastes a template with its lowest corner at origin.",
            input: {
                action: z.enum(["list", "save", "place"]),
                id: z
                    .string()
                    .optional()
                    .describe('Template id for save and place, e.g. "builds:house".'),
                filter: z.string().default("").describe("Substring of the id, for list."),
                limit,
                dimension,
                from: blockPos.optional().describe("Region corner for save."),
                to: blockPos.optional().describe("Opposite region corner for save."),
                origin: blockPos.optional().describe("Required for place."),
                rotation: z
                    .enum(["none", "clockwise_90", "clockwise_180", "counterclockwise_90"])
                    .default("none"),
                mirror: z.enum(["none", "left_right", "front_back"]).default("none"),
                include_entities: z.boolean().default(true),
                integrity: z
                    .number()
                    .min(0)
                    .max(1)
                    .default(1)
                    .describe("Chance each block is placed."),
                ignore_block: z
                    .string()
                    .default("")
                    .describe('For save: block left out, e.g. "minecraft:structure_void".'),
            },
            actions: {
                list: {
                    access: "read",
                    run: (input) =>
                        call(structure, "list", {}).map((list) => {
                            const matching = list.ids.filter((id) => id.includes(input.filter));
                            const page = matching.slice(0, input.limit);
                            return lines(`${matching.length} matching templates`, page, {
                                total: matching.length,
                                ids: page,
                            });
                        }),
                },
                save: {
                    access: "write",
                    run: (input) =>
                        required(input, "id", "from", "to").andThenAsync((request) =>
                            call(structure, "save", {
                                ...request,
                                includeEntities: input.include_entities,
                                ignoreBlock: input.ignore_block,
                            }).map(() => done(`Saved ${request.id}`)),
                        ),
                },
                place: {
                    access: "write",
                    run: (input) =>
                        required(input, "id", "origin").andThenAsync(({ id, origin }) =>
                            call(structure, "place", {
                                id,
                                placement: {
                                    dimension: input.dimension,
                                    origin,
                                    rotation: ROTATIONS[input.rotation],
                                    mirror: MIRRORS[input.mirror],
                                    includeEntities: input.include_entities,
                                    integrity: input.integrity,
                                },
                            }).map(json),
                        ),
                },
            },
        }),
    ];
}
