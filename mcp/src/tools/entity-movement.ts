import type { ConduitClients } from "@conduit/client";
import * as z from "zod";
import { done } from "../format.ts";
import { call } from "../grpc.ts";
import { rotation, uuid, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";
import { describeEntity } from "./entities.ts";

export function entityMovementTools({ entity }: ConduitClients) {
    return [
        defineTool({
            name: "move_entity",
            title: "Move entity",
            description:
                "teleport moves an entity, players included, also across dimensions. rotate turns " +
                "it. push sets its velocity in blocks per tick. mount puts it on a vehicle, and " +
                "dismount takes it off.",
            input: {
                action: z.enum(["teleport", "rotate", "push", "mount", "dismount"]),
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
                vehicle: z.string().optional().describe("Vehicle UUID. Required for mount."),
            },
            actions: {
                teleport: {
                    access: "write",
                    run: (input) =>
                        required(input, "position").andThenAsync((request) =>
                            call(entity, "teleport", request).map((moved) =>
                                done(describeEntity(moved)),
                            ),
                        ),
                },
                rotate: {
                    access: "write",
                    run: (input) =>
                        required(input, "rotation").andThenAsync((request) =>
                            call(entity, "setRotation", request).map(() => done("Entity rotated")),
                        ),
                },
                push: {
                    access: "write",
                    run: (input) =>
                        required(input, "velocity").andThenAsync((request) =>
                            call(entity, "setVelocity", request).map(() => done("Velocity set")),
                        ),
                },
                mount: {
                    access: "write",
                    run: (input) =>
                        required(input, "vehicle").andThenAsync(({ vehicle }) =>
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
