import type { ConduitClients } from "@conduit/client";
import { z } from "zod";
import { compact, done, json, lines, position } from "../format.ts";
import { call } from "../grpc.ts";
import { player } from "../schemas.ts";
import { defineTool } from "../tool.ts";

interface Slot {
    slot: number;
    item: { id: string; count: number; components: string } | null;
}

export function playerTools({ player: players }: ConduitClients) {
    return [
        defineTool({
            name: "list_players",
            seesOtherPlayers: true,
            title: "List players",
            description:
                "online: online players with game mode, position, health and food. known: every " +
                "player with saved data, also offline ones.",
            input: { action: z.enum(["online", "known"]).default("online") },
            actions: {
                online: {
                    access: "read",
                    run: () =>
                        call(players, "listPlayers", {}).map((list) =>
                            lines(
                                `${list.players.length} players online`,
                                list.players.map(
                                    (online) =>
                                        `${online.name} ${online.entity?.uuid} ${online.gameMode} ` +
                                        `${online.entity?.dimension} ${position(online.entity?.position ?? null)} ` +
                                        `${online.entity?.health}hp food ${online.foodLevel}`,
                                ),
                                { players: list.players.map(compact) },
                            ),
                        ),
                },
                known: {
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
                                            : ` last played ${new Date(Number(known.lastPlayed)).toISOString()}`),
                                ),
                                { players: list.players },
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "get_player",
            title: "Get player",
            description:
                "info, inventory and ender_chest read an online player. Inventory slots: 0-8 " +
                "hotbar, 9-35 main, 36-39 armor (feet, legs, chest, head), 40 offhand. stats reads " +
                "the statistics of any player, filtered by a substring. saved and saved_nbt read the " +
                "saved data of any player, also offline ones.",
            input: {
                action: z
                    .enum(["info", "inventory", "ender_chest", "stats", "saved", "saved_nbt"])
                    .default("info"),
                player,
                stats_filter: z.string().default("").describe('e.g. "mined" or "diamond".'),
            },
            actions: {
                info: {
                    access: "read",
                    run: (input) => call(players, "getPlayer", input).map(json),
                },
                inventory: {
                    access: "read",
                    run: (input) =>
                        call(players, "getInventory", input).map((inventory) =>
                            describeInventory(
                                `Selected hotbar slot ${inventory.selectedSlot}; non-empty slots:`,
                                inventory,
                            ),
                        ),
                },
                ender_chest: {
                    access: "read",
                    run: (input) =>
                        call(players, "getEnderChest", input).map((inventory) =>
                            describeInventory("Non-empty ender chest slots:", inventory),
                        ),
                },
                stats: {
                    access: "read",
                    run: (input) =>
                        call(players, "getPlayerStats", input).map(({ stats }) =>
                            json(
                                Object.fromEntries(
                                    Object.entries(stats).filter(([name]) =>
                                        name.includes(input.stats_filter),
                                    ),
                                ),
                            ),
                        ),
                },
                saved: {
                    access: "read",
                    run: (input) => call(players, "getOfflinePlayer", input).map(json),
                },
                saved_nbt: {
                    access: "read",
                    run: (input) =>
                        call(players, "getOfflinePlayerNbt", input).map((nbt) => done(nbt.snbt)),
                },
            },
        }),
    ];
}

function describeInventory(header: string, inventory: { slots: Slot[] }) {
    return lines(
        header,
        inventory.slots.map(
            ({ slot, item: stack }) =>
                `${slot}: ${stack?.count} ${stack?.id}${stack?.components ? ` ${stack.components}` : ""}`,
        ),
        { slots: inventory.slots.map(compact) },
    );
}
