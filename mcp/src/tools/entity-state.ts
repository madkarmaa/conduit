import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json } from "../format.ts";
import { call } from "../grpc.ts";
import { uuid } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function entityStateTools({ entity }: ConduitClients) {
    return [
        defineTool({
            name: "entity_effects",
            title: "Entity effects",
            description: "Lists, adds or removes status effects of a living entity.",
            input: {
                action: z.enum(["list", "add", "remove"]),
                uuid,
                effect: z
                    .string()
                    .optional()
                    .describe(
                        'Effect id, e.g. "minecraft:speed". For remove, leave out to clear all.',
                    ),
                duration_ticks: z.int().min(-1).default(600).describe("-1 means infinite."),
                amplifier: z.int().min(0).max(255).default(0).describe("0 is level I."),
                show_particles: z.boolean().default(true),
            },
            actions: {
                list: {
                    access: "read",
                    run: (input) => call(entity, "getEffects", input).map(json),
                },
                add: {
                    access: "write",
                    run: (input) =>
                        required(input.effect, "effect").andThenAsync((id) =>
                            call(entity, "addEffect", {
                                uuid: input.uuid,
                                effect: {
                                    id,
                                    duration: input.duration_ticks,
                                    amplifier: input.amplifier,
                                    visible: input.show_particles,
                                    showIcon: true,
                                },
                            }).map(() => done(`Added ${id}`)),
                        ),
                },
                remove: {
                    access: "write",
                    run: (input) =>
                        call(entity, "removeEffect", input).map(() => done("Effects removed")),
                },
            },
        }),

        defineTool({
            name: "entity_attributes",
            title: "Entity attributes",
            description:
                "Lists the attributes of a living entity (base and current value) or sets a base " +
                'value, e.g. "minecraft:max_health" or "minecraft:movement_speed".',
            input: {
                action: z.enum(["list", "set_base"]),
                uuid,
                attribute: z.string().optional().describe("Required for set_base."),
                base: z.number().optional().describe("Required for set_base."),
            },
            actions: {
                list: {
                    access: "read",
                    run: (input) => call(entity, "getAttributes", input).map(json),
                },
                set_base: {
                    access: "write",
                    run: (input) =>
                        required(input.attribute, "attribute")
                            .andThen((attribute) =>
                                required(input.base, "base").map((base) => ({ attribute, base })),
                            )
                            .andThenAsync((change) =>
                                call(entity, "setAttributeBase", {
                                    uuid: input.uuid,
                                    ...change,
                                }).map(() =>
                                    done(`${change.attribute} base set to ${change.base}`),
                                ),
                            ),
                },
            },
        }),

        defineTool({
            name: "ride",
            title: "Ride",
            description: "Puts an entity on a vehicle, or makes it dismount.",
            input: {
                action: z.enum(["mount", "dismount"]),
                uuid: uuid.describe("UUID of the passenger."),
                vehicle: z.string().optional().describe("Vehicle UUID; required for mount."),
            },
            actions: {
                mount: {
                    access: "write",
                    run: (input) =>
                        required(input.vehicle, "vehicle").andThenAsync((vehicle) =>
                            call(entity, "mount", { passenger: input.uuid, vehicle }).map(() =>
                                done("Mounted"),
                            ),
                        ),
                },
                dismount: {
                    access: "write",
                    run: (input) => call(entity, "dismount", input).map(() => done("Dismounted")),
                },
            },
        }),
    ];
}
