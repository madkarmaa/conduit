import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done, json, lines } from "../format.ts";
import { call } from "../grpc.ts";
import { item, player, snbt } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

export function offlinePlayerTools({ player: players }: ConduitClients) {
    return [
        defineTool({
            name: "offline_player",
            title: "Saved player data",
            description:
                "Saved data of any player, also offline ones: list_known lists every player with " +
                "saved data, get reads position, health, inventory and more, nbt reads the raw " +
                "SNBT. merge_nbt and set_slot edit the saved data of offline players only; edits " +
                "apply at their next login.",
            input: {
                action: z.enum(["list_known", "get", "nbt", "merge_nbt", "set_slot"]),
                player: player
                    .optional()
                    .describe("Player name or UUID; required except for list_known."),
                snbt: snbt.optional().describe("Required for merge_nbt."),
                slot: z.int().min(0).max(40).optional().describe("Required for set_slot."),
                item: item.optional().describe("For set_slot; leave out to empty the slot."),
            },
            actions: {
                list_known: {
                    access: "read",
                    run: () =>
                        call(players, "listKnownPlayers", {}).map((list) =>
                            lines(
                                `${list.players.length} known players`,
                                list.players.map(
                                    (known) =>
                                        `${known.name || "?"} ${known.uuid}` +
                                        (known.online
                                            ? " online"
                                            : ` last played ${lastPlayed(known.lastPlayed)}`),
                                ),
                                { players: list.players },
                            ),
                        ),
                },
                get: {
                    access: "read",
                    run: (input) =>
                        required(input.player, "player").andThenAsync((name) =>
                            call(players, "getOfflinePlayer", { player: name }).map(json),
                        ),
                },
                nbt: {
                    access: "read",
                    run: (input) =>
                        required(input.player, "player").andThenAsync((name) =>
                            call(players, "getOfflinePlayerNbt", { player: name }).map((nbt) =>
                                done(nbt.snbt),
                            ),
                        ),
                },
                merge_nbt: {
                    access: "destructive",
                    run: (input) =>
                        required(input.player, "player")
                            .andThen((name) =>
                                required(input.snbt, "snbt").map((data) => ({
                                    player: name,
                                    snbt: data,
                                })),
                            )
                            .andThenAsync((request) =>
                                call(players, "mergeOfflinePlayerNbt", request).map(() =>
                                    done("Saved data merged"),
                                ),
                            ),
                },
                set_slot: {
                    access: "write",
                    run: (input) =>
                        required(input.player, "player")
                            .andThen((name) =>
                                required(input.slot, "slot").map((slot) => ({
                                    player: name,
                                    slot,
                                })),
                            )
                            .andThenAsync((request) =>
                                call(players, "setOfflineInventorySlot", {
                                    ...request,
                                    item: input.item,
                                }).map(() => done(`Slot ${request.slot} set`)),
                            ),
                },
            },
        }),
    ];
}

function lastPlayed(milliseconds: string): string {
    return new Date(Number(milliseconds)).toISOString();
}
