import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json, lines } from "../format.ts";
import { call } from "../grpc.ts";
import { rotation, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function serverTools({ server, world }: ConduitClients) {
    return [
        defineTool({
            name: "server_info",
            title: "Server info",
            description:
                "summary: Minecraft and mod versions, players, seed, difficulty, performance (TPS, " +
                "MSPT, memory) and dimensions with their height range. mods, datapacks and backups " +
                "list those.",
            input: {
                action: z.enum(["summary", "mods", "datapacks", "backups"]).default("summary"),
            },
            actions: {
                summary: {
                    access: "read",
                    run: () =>
                        call(server, "getInfo", {}).andThenAsync((info) =>
                            call(server, "getPerformance", {}).andThenAsync((performance) =>
                                call(world, "listDimensions", {}).map(({ dimensions }) =>
                                    json({ ...info, mods: [], dimensions, performance }),
                                ),
                            ),
                        ),
                },
                mods: {
                    access: "read",
                    run: () =>
                        call(server, "getInfo", {}).map(({ mods }) =>
                            lines(`${mods.length} mods and libraries`, mods, { mods }),
                        ),
                },
                datapacks: {
                    access: "read",
                    run: () => call(server, "listDatapacks", {}).map(json),
                },
                backups: {
                    access: "read",
                    run: () =>
                        call(server, "listBackups", {}).map(({ backups }) =>
                            lines(
                                `${backups.length} backups`,
                                backups.map(
                                    (backup) =>
                                        `${backup.name} ${backup.sizeBytes} bytes, created ` +
                                        new Date(Number(backup.createdTimestampMs)).toISOString(),
                                ),
                                { backups },
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "run_command",
            title: "Run command",
            description:
                "Runs any command like the server console, and returns whether it succeeded, its " +
                "result value and its feedback. Can run as a player or entity and at a position.",
            input: {
                command: z
                    .string()
                    .describe('Command with or without the slash, e.g. "time set day".'),
                as: z
                    .string()
                    .optional()
                    .describe("Player name or UUID, or entity UUID to run as."),
                dimension: z.string().optional(),
                position: vec3.optional(),
                rotation: rotation.optional(),
                permission_level: z.int().min(0).max(4).default(4),
            },
            actions: {
                run: {
                    access: "write",
                    run: (input) =>
                        call(server, "executeCommand", {
                            ...input,
                            permissionLevel: input.permission_level,
                        }).map((response) =>
                            lines(
                                `${response.success ? "Succeeded" : "Failed"}, result ${response.result}`,
                                response.output,
                                { ...response },
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "server_admin",
            title: "Server administration",
            description:
                "save writes worlds and players to disk, backup saves the world and zips it while " +
                "the server keeps running, set_datapack enables or disables a datapack and reloads " +
                "the data, stop saves and stops the server.",
            input: {
                action: z.enum(["save", "backup", "set_datapack", "stop"]),
                flush: z
                    .boolean()
                    .default(false)
                    .describe("save waits until everything is written."),
                name: z.string().max(64).optional().describe("Appended to the backup file name."),
                id: z
                    .string()
                    .optional()
                    .describe('Datapack id for set_datapack, e.g. "file/my_pack".'),
                enabled: z.boolean().optional().describe("Required for set_datapack."),
            },
            actions: {
                save: {
                    access: "write",
                    run: (input) => call(server, "save", input).map(() => done("Saved")),
                },
                backup: {
                    access: "write",
                    run: (input) => call(server, "backup", input).map(json),
                },
                set_datapack: {
                    access: "write",
                    run: (input) =>
                        required(input, "id", "enabled").andThenAsync((request) =>
                            call(server, "setDatapackEnabled", request).map(json),
                        ),
                },
                stop: {
                    access: "write",
                    run: () => call(server, "stop", {}).map(() => done("Server stopping")),
                },
            },
        }),
    ];
}
