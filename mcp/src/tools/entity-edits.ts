import type { ConduitClients } from "@conduit/client";
import { z } from "zod";
import { done } from "../format.ts";
import { call } from "../grpc.ts";
import { snbt, text, toText, uuid } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function entityEditTools({ entity }: ConduitClients) {
    return [
        defineTool({
            name: "edit_entity",
            title: "Edit entity",
            description:
                "name sets or clears the custom name. tags adds and removes scoreboard tags. health " +
                "sets the health. damage hurts the entity like an attack. nbt merges SNBT into its " +
                "data. add_effect and remove_effect change status effects. set_attribute sets an " +
                'attribute base value such as "minecraft:max_health".',
            input: {
                action: z.enum([
                    "name",
                    "tags",
                    "health",
                    "damage",
                    "nbt",
                    "add_effect",
                    "remove_effect",
                    "set_attribute",
                ]),
                uuid,
                name: text.optional().describe("New custom name. Leave it out to clear it."),
                name_visible: z.boolean().default(false),
                add_tags: z.array(z.string()).optional(),
                remove_tags: z.array(z.string()).optional(),
                amount: z.number().min(0).optional().describe("Health or damage amount."),
                damage_type: z.string().default("minecraft:generic"),
                attacker: z.string().optional().describe("UUID of the attacking entity."),
                snbt: snbt.optional().describe("Required for nbt."),
                effect: z
                    .string()
                    .optional()
                    .describe(
                        'Effect id, e.g. "minecraft:speed"; remove_effect without it clears all.',
                    ),
                duration_ticks: z.int().min(-1).default(600).describe("-1 means infinite."),
                amplifier: z.int().min(0).max(255).default(0).describe("0 is level I."),
                show_particles: z.boolean().default(true),
                attribute: z.string().optional().describe("Required for set_attribute."),
                base: z.number().optional().describe("Required for set_attribute."),
            },
            actions: {
                name: {
                    access: "write",
                    run: (input) =>
                        call(entity, "setCustomName", {
                            uuid: input.uuid,
                            name: input.name === undefined ? undefined : toText(input.name),
                            visible: input.name_visible,
                        }).map(() => done(input.name ? "Name set" : "Name cleared")),
                },
                tags: {
                    access: "write",
                    run: (input) =>
                        call(entity, "setTags", {
                            uuid: input.uuid,
                            add: input.add_tags,
                            remove: input.remove_tags,
                        }).map(() => done("Tags updated")),
                },
                health: {
                    access: "write",
                    run: (input) =>
                        required(input, "amount").andThenAsync(({ amount }) =>
                            call(entity, "setHealth", { uuid: input.uuid, health: amount }).map(
                                () => done(`Health set to ${amount}`),
                            ),
                        ),
                },
                damage: {
                    access: "write",
                    run: (input) =>
                        required(input, "amount").andThenAsync((request) =>
                            call(entity, "damage", {
                                ...request,
                                damageType: input.damage_type,
                            }).map(() => done(`Dealt ${request.amount} damage`)),
                        ),
                },
                nbt: {
                    access: "write",
                    run: (input) =>
                        required(input, "snbt").andThenAsync((request) =>
                            call(entity, "mergeNbt", request).map(() => done("Entity data merged")),
                        ),
                },
                add_effect: {
                    access: "write",
                    run: (input) =>
                        required(input, "effect").andThenAsync(({ effect }) =>
                            call(entity, "addEffect", {
                                uuid: input.uuid,
                                effect: {
                                    id: effect,
                                    duration: input.duration_ticks,
                                    amplifier: input.amplifier,
                                    visible: input.show_particles,
                                    showIcon: true,
                                },
                            }).map(() => done(`Added ${effect}`)),
                        ),
                },
                remove_effect: {
                    access: "write",
                    run: (input) =>
                        call(entity, "removeEffect", input).map(() => done("Effects removed")),
                },
                set_attribute: {
                    access: "write",
                    run: (input) =>
                        required(input, "attribute", "base").andThenAsync((request) =>
                            call(entity, "setAttributeBase", request).map(() =>
                                done(`${request.attribute} base set to ${request.base}`),
                            ),
                        ),
                },
            },
        }),
    ];
}
