import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { compact, done, json, lines, position, round } from "../format.ts";
import { call } from "../grpc.ts";
import { dimension, limit, rotation, snbt, uuid, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

interface Entity {
    uuid: string;
    type: string;
    customName?: string;
    dimension: string;
    position: { x: number; y: number; z: number } | null;
    health?: number;
    maxHealth?: number;
}

export function entityTools({ entity }: ConduitClients) {
    return [
        defineTool({
            name: "list_entities",
            title: "List entities",
            description:
                "Finds entities by type, area, tag or a vanilla selector. Lists one entity per line: " +
                "uuid, type, name, dimension, position and health.",
            input: {
                dimension: z.string().optional().describe("Empty means every dimension."),
                types: z.array(z.string()).optional().describe('e.g. ["minecraft:zombie"].'),
                center: vec3.optional().describe("Requires dimension and radius."),
                radius: z.number().min(0).optional(),
                box: z.object({ min: vec3, max: vec3 }).optional(),
                tag: z.string().optional().describe("Required scoreboard tag."),
                selector: z
                    .string()
                    .optional()
                    .describe(
                        'Selector such as "@e[type=cow,distance=..10]"; overrides other filters.',
                    ),
                limit,
            },
            actions: {
                list: {
                    access: "read",
                    run: (input) =>
                        call(entity, "listEntities", input).map((list) =>
                            lines(
                                `${list.entities.length} entities`,
                                list.entities.map(describeEntity),
                                { entities: list.entities.map(compact) },
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "get_entity",
            title: "Get entity",
            description: "Full state of one entity, or its complete NBT data as SNBT.",
            input: { uuid, detail: z.enum(["info", "nbt"]).default("info") },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        input.detail === "nbt"
                            ? call(entity, "getNbt", input).map((nbt) => done(nbt.snbt))
                            : call(entity, "getEntity", input).map(json),
                },
            },
        }),
        defineTool({
            name: "spawn_entity",
            title: "Spawn entity",
            description:
                "Spawns an entity in a loaded chunk and returns it. Use the chunk tool to " +
                "force-load chunks away from players.",
            input: {
                type: z.string().describe('Entity type, e.g. "minecraft:villager".'),
                dimension,
                position: vec3,
                rotation: rotation.optional(),
                snbt: snbt
                    .optional()
                    .describe("Data applied on spawn, like the /summon nbt argument."),
                initialize: z
                    .boolean()
                    .default(true)
                    .describe("Run natural spawn setup (equipment, variants)."),
            },
            actions: {
                spawn: {
                    access: "write",
                    run: (input) => call(entity, "spawnEntity", input).map(json),
                },
            },
        }),
        defineTool({
            name: "remove_entity",
            title: "Remove entity",
            description: "Removes an entity silently, or kills it so it drops loot.",
            input: { uuid, kill: z.boolean().default(false) },
            actions: {
                remove: {
                    access: "destructive",
                    run: (input) =>
                        call(entity, "removeEntity", input).map(() => done("Entity removed")),
                },
            },
        }),
        defineTool({
            name: "move_entity",
            title: "Move entity",
            description:
                "Teleports an entity (players too, also across dimensions), turns it, or pushes it " +
                "with a velocity in blocks per tick.",
            input: {
                action: z.enum(["teleport", "rotate", "push"]),
                uuid,
                dimension: z.string().optional().describe("Target dimension for teleport."),
                position: vec3.optional().describe("Required for teleport."),
                rotation: rotation
                    .optional()
                    .describe("Required for rotate, optional for teleport."),
                velocity: vec3.optional().describe("Required for push."),
                add: z
                    .boolean()
                    .default(false)
                    .describe("push adds to the current velocity instead of replacing it."),
            },
            actions: {
                teleport: {
                    access: "write",
                    run: (input) =>
                        required(input.position, "position").andThenAsync((destination) =>
                            call(entity, "teleport", { ...input, position: destination }).map(
                                (moved) => done(describeEntity(moved)),
                            ),
                        ),
                },
                rotate: {
                    access: "write",
                    run: (input) =>
                        required(input.rotation, "rotation").andThenAsync((facing) =>
                            call(entity, "setRotation", { uuid: input.uuid, rotation: facing }).map(
                                () => done("Entity rotated"),
                            ),
                        ),
                },
                push: {
                    access: "write",
                    run: (input) =>
                        required(input.velocity, "velocity").andThenAsync((velocity) =>
                            call(entity, "setVelocity", { ...input, velocity }).map(() =>
                                done("Velocity set"),
                            ),
                        ),
                },
            },
        }),
    ];
}

export function describeEntity(entity: Entity): string {
    const health =
        entity.health === undefined
            ? ""
            : ` ${round(entity.health)}/${round(entity.maxHealth ?? entity.health)}hp`;
    const name = entity.customName ? ` "${entity.customName}"` : "";
    return `${entity.uuid} ${entity.type}${name} ${entity.dimension} ${position(entity.position)}${health}`;
}
