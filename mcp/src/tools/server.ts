import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json, lines } from "../format.ts";
import { call } from "../grpc.ts";
import { rotation, text, toText, vec3 } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function serverTools({ server }: ConduitClients) {
    return [
        defineTool({
            name: "server_info",
            title: "Server info",
            description:
                "Minecraft and mod versions, players, dimensions, seed, difficulty and performance " +
                "(TPS, MSPT, memory), optionally with the installed mods and libraries.",
            input: { include_mods: z.boolean().default(false) },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        call(server, "getInfo", {}).andThenAsync((info) =>
                            call(server, "getPerformance", {}).map((performance) =>
                                json({
                                    ...info,
                                    mods: input.include_mods ? info.mods : [],
                                    performance,
                                }),
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
                    access: "destructive",
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
            name: "broadcast",
            title: "Broadcast",
            description: "Sends a message to every player, in the chat or the action bar.",
            input: { message: text, action_bar: z.boolean().default(false) },
            actions: {
                send: {
                    access: "write",
                    run: (input) =>
                        call(server, "broadcast", {
                            message: toText(input.message),
                            overlay: input.action_bar,
                        }).map(() => done("Broadcast sent")),
                },
            },
        }),
        defineTool({
            name: "save",
            title: "Save world",
            description: "Saves all worlds and players to disk.",
            input: {
                flush: z.boolean().default(false).describe("Wait until everything is written."),
            },
            actions: {
                save: {
                    access: "write",
                    run: (input) => call(server, "save", input).map(() => done("Saved")),
                },
            },
        }),
        defineTool({
            name: "stop",
            title: "Stop server",
            description: "Saves and stops the Minecraft server, which ends this connection.",
            input: {},
            actions: {
                stop: {
                    access: "destructive",
                    run: () => call(server, "stop", {}).map(() => done("Server stopping")),
                },
            },
        }),
        defineTool({
            name: "datapacks",
            title: "Datapacks",
            description: "Lists datapacks, or enables or disables one and reloads the server data.",
            input: {
                action: z.enum(["list", "set_enabled"]),
                id: z
                    .string()
                    .optional()
                    .describe('Datapack id, e.g. "file/my_pack"; for set_enabled.'),
                enabled: z.boolean().optional().describe("Required for set_enabled."),
            },
            actions: {
                list: { access: "read", run: () => call(server, "listDatapacks", {}).map(json) },
                set_enabled: {
                    access: "destructive",
                    run: (input) =>
                        required(input.id, "id")
                            .andThen((id) =>
                                required(input.enabled, "enabled").map((enabled) => ({
                                    id,
                                    enabled,
                                })),
                            )
                            .andThenAsync((request) =>
                                call(server, "setDatapackEnabled", request).map(json),
                            ),
                },
            },
        }),
        defineTool({
            name: "backups",
            title: "Backups",
            description:
                "Lists world backups, or saves the world and zips it while the server keeps running.",
            input: {
                action: z.enum(["list", "create"]),
                name: z.string().max(64).optional().describe("Appended to the backup file name."),
            },
            actions: {
                list: {
                    access: "read",
                    run: () =>
                        call(server, "listBackups", {}).map((list) =>
                            lines(
                                `${list.backups.length} backups`,
                                list.backups.map(
                                    (backup) =>
                                        `${backup.name} ${backup.sizeBytes} bytes, created ` +
                                        new Date(Number(backup.createdTimestampMs)).toISOString(),
                                ),
                                { backups: list.backups },
                            ),
                        ),
                },
                create: {
                    access: "write",
                    run: (input) => call(server, "backup", input).map(json),
                },
            },
        }),
    ];
}
