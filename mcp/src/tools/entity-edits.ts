import type { ConduitClients } from "conduit-client";
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
                "Changes an entity: name sets or clears its custom name, tags adds and removes " +
                "scoreboard tags, health sets its health, damage hurts it like an attack, nbt " +
                "merges SNBT into its data.",
            input: {
                action: z.enum(["name", "tags", "health", "damage", "nbt"]),
                uuid,
                name: text.optional().describe("New custom name for name; leave out to clear it."),
                name_visible: z.boolean().default(false),
                add_tags: z.array(z.string()).optional(),
                remove_tags: z.array(z.string()).optional(),
                amount: z.number().min(0).optional().describe("Health or damage amount."),
                damage_type: z.string().default("minecraft:generic"),
                attacker: z.string().optional().describe("UUID of the attacking entity."),
                snbt: snbt.optional().describe("Required for nbt."),
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
                        required(input.amount, "amount").andThenAsync((health) =>
                            call(entity, "setHealth", { uuid: input.uuid, health }).map(() =>
                                done(`Health set to ${health}`),
                            ),
                        ),
                },
                damage: {
                    access: "write",
                    run: (input) =>
                        required(input.amount, "amount").andThenAsync((amount) =>
                            call(entity, "damage", {
                                uuid: input.uuid,
                                amount,
                                damageType: input.damage_type,
                                attacker: input.attacker,
                            }).map(() => done(`Dealt ${amount} damage`)),
                        ),
                },
                nbt: {
                    access: "write",
                    run: (input) =>
                        required(input.snbt, "snbt").andThenAsync((data) =>
                            call(entity, "mergeNbt", { uuid: input.uuid, snbt: data }).map(() =>
                                done("Entity data merged"),
                            ),
                        ),
                },
            },
        }),
    ];
}
