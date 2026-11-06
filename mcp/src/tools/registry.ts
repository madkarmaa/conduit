import type { ConduitClients } from "@conduit/client";
import { z } from "zod";
import { done, lines } from "../format.ts";
import { call } from "../grpc.ts";
import { limit } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

const offset = z.int().min(0).default(0);

export function registryTools({ registry }: ConduitClients) {
    return [
        defineTool({
            name: "registry",
            title: "Game registries",
            description:
                "Looks up valid ids, including datapack content. registries lists every registry, " +
                'such as "minecraft:block", "minecraft:item", "minecraft:entity_type" and ' +
                '"minecraft:worldgen/biome". entries lists the ids in one. tags lists its tags.',
            input: {
                action: z.enum(["registries", "entries", "tags"]),
                registry: z.string().optional().describe("Required for entries and tags."),
                filter: z.string().default("").describe("Case-insensitive substring of the id."),
                tag: z
                    .string()
                    .optional()
                    .describe('For entries: only members of this tag, e.g. "minecraft:logs".'),
                offset,
                limit,
            },
            actions: {
                registries: {
                    access: "read",
                    run: (input) =>
                        call(registry, "listRegistries", {}).map((list) => {
                            const { total, items } = page(list.registries, input);
                            return lines(
                                `${total} matching registries`,
                                items.map((entry) => `${entry.id} (${entry.entryCount})`),
                                { total, registries: items },
                            );
                        }),
                },
                entries: {
                    access: "read",
                    run: (input) =>
                        required(input, "registry").andThenAsync((request) =>
                            call(registry, "listRegistryEntries", request).map((list) => {
                                const ids = list.entries.map((entry) => entry.id);
                                return lines(`${list.total} matching entries`, ids, {
                                    total: list.total,
                                    ids,
                                });
                            }),
                        ),
                },
                tags: {
                    access: "read",
                    run: (input) =>
                        required(input, "registry").andThenAsync((request) =>
                            call(registry, "listTags", request).map((list) => {
                                const { total, items } = page(list.tags, input);
                                return lines(
                                    `${total} matching tags`,
                                    items.map((tag) => `#${tag.id}: ${tag.entries.join(" ")}`),
                                    { total, tags: items },
                                );
                            }),
                        ),
                },
            },
        }),

        defineTool({
            name: "recipes",
            title: "Recipes and loot tables",
            description:
                "list finds recipe ids, optionally by type or result item. get reads a recipe and " +
                'loot_table a loot table, such as "minecraft:blocks/diamond_ore", as datapack JSON.',
            input: {
                action: z.enum(["list", "get", "loot_table"]),
                id: z
                    .string()
                    .optional()
                    .describe(
                        'Recipe or loot table id for get and loot_table, e.g. "minecraft:crafting_table".',
                    ),
                type: z.string().default("").describe('e.g. "minecraft:crafting_shaped".'),
                result_item: z.string().default("").describe('e.g. "minecraft:torch".'),
                offset,
                limit,
            },
            actions: {
                list: {
                    access: "read",
                    run: (input) =>
                        call(registry, "listRecipes", {
                            ...input,
                            resultItem: input.result_item,
                        }).map((list) =>
                            lines(
                                `${list.total} matching recipes`,
                                list.recipes.map((recipe) => `${recipe.id} (${recipe.type})`),
                                { total: list.total, ids: list.recipes.map((recipe) => recipe.id) },
                            ),
                        ),
                },
                get: {
                    access: "read",
                    run: (input) =>
                        required(input, "id").andThenAsync((request) =>
                            call(registry, "getRecipe", request).map((recipe) => done(recipe.json)),
                        ),
                },
                loot_table: {
                    access: "read",
                    run: (input) =>
                        required(input, "id").andThenAsync((request) =>
                            call(registry, "getLootTable", request).map((table) =>
                                done(table.json),
                            ),
                        ),
                },
            },
        }),
    ];
}

interface PageQuery {
    filter: string;
    offset: number;
    limit: number;
}

// Registries and tags come back whole, so they are filtered and paged here like the entries.
function page<Item extends { id: string }>(all: Item[], query: PageQuery) {
    const filter = query.filter.toLowerCase();
    const matching = all.filter((item) => item.id.toLowerCase().includes(filter));

    return {
        total: matching.length,
        items: matching.slice(query.offset, query.offset + query.limit),
    };
}
