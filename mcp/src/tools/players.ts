import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { compact, done, json, lines, position } from "../format.ts";
import { call } from "../grpc.ts";
import { item, player } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

const SLOTS = "Slots: 0-8 hotbar, 9-35 main, 36-39 armor (feet, legs, chest, head), 40 offhand.";

interface Slot {
    slot: number;
    item: { id: string; count: number; components: string } | null;
}

export function playerTools({ player: players }: ConduitClients) {
    return [
        defineTool({
            name: "list_players",
            title: "List players",
            description: "Online players with game mode, position, health and food.",
            input: {},
            actions: {
                list: {
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
            },
        }),

        defineTool({
            name: "get_player",
            title: "Get player",
            description:
                "Details of an online player, or the statistics of any player (mined, crafted, " +
                "killed, ...). Statistics are filtered by a substring of their name.",
            input: {
                player,
                detail: z.enum(["info", "stats"]).default("info"),
                stats_filter: z.string().default("").describe('e.g. "mined" or "diamond".'),
            },
            actions: {
                get: {
                    access: "read",
                    run: (input) =>
                        input.detail === "info"
                            ? call(players, "getPlayer", input).map(json)
                            : call(players, "getPlayerStats", input).map((stats) =>
                                  json(
                                      Object.fromEntries(
                                          Object.entries(stats.stats).filter(([name]) =>
                                              name.includes(input.stats_filter),
                                          ),
                                      ),
                                  ),
                              ),
                },
            },
        }),

        defineTool({
            name: "inventory",
            title: "Inventory",
            description: `Reads or edits an online player's inventory or ender chest. ${SLOTS}`,
            input: {
                action: z.enum(["get", "ender_chest", "set_slot", "clear", "select_slot"]),
                player,
                slot: z
                    .int()
                    .min(0)
                    .max(40)
                    .optional()
                    .describe("Required for set_slot and select_slot."),
                item: item.optional().describe("For set_slot; leave out to empty the slot."),
            },
            actions: {
                get: {
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
                set_slot: {
                    access: "write",
                    run: (input) =>
                        required(input.slot, "slot").andThenAsync((slot) =>
                            call(players, "setInventorySlot", { ...input, slot }).map(() =>
                                done(`Slot ${slot} set`),
                            ),
                        ),
                },
                clear: {
                    access: "write",
                    run: (input) =>
                        call(players, "clearInventory", input).map(() => done("Inventory cleared")),
                },
                select_slot: {
                    access: "write",
                    run: (input) =>
                        required(input.slot, "slot").andThenAsync((slot) =>
                            call(players, "setSelectedSlot", { ...input, slot }).map(() =>
                                done(`Selected hotbar slot ${slot}`),
                            ),
                        ),
                },
            },
        }),

        defineTool({
            name: "give_item",
            title: "Give item",
            description: "Gives an item stack to an online player; what does not fit is dropped.",
            input: { player, item },
            actions: {
                give: {
                    access: "write",
                    run: (input) =>
                        call(players, "giveItem", input).map((response) =>
                            done(
                                `Gave ${input.item.count} ${input.item.id}` +
                                    (response.dropped ? `, ${response.dropped} dropped` : ""),
                            ),
                        ),
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
