import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { done } from "../format.ts";
import { call } from "../grpc.ts";
import { item, player } from "../schemas.ts";
import { defineTool, required } from "../tool.ts";

const slot = z.int().min(0).max(40).optional();

export function inventoryTools({ player: players }: ConduitClients) {
    return [
        defineTool({
            name: "inventory",
            title: "Edit inventory",
            description:
                "Edits an online player's inventory. set_slot puts an item in a slot or empties it. " +
                "clear empties the inventory. select_slot picks the held hotbar slot. Slots 0-8 are " +
                "the hotbar, 9-35 the main inventory, 36-39 armor (feet, legs, chest, head) and 40 " +
                "the offhand.",
            input: {
                action: z.enum(["set_slot", "clear", "select_slot"]),
                player,
                slot: slot.describe("Required for set_slot and select_slot."),
                item: item.optional().describe("For set_slot. Leave it out to empty the slot."),
            },
            actions: {
                set_slot: {
                    access: "write",
                    run: (input) =>
                        required(input, "slot").andThenAsync((request) =>
                            call(players, "setInventorySlot", request).map(() =>
                                done(`Slot ${request.slot} set`),
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
                        required(input, "slot").andThenAsync((request) =>
                            call(players, "setSelectedSlot", request).map(() =>
                                done(`Selected hotbar slot ${request.slot}`),
                            ),
                        ),
                },
            },
        }),
        defineTool({
            name: "give_item",
            title: "Give item",
            description:
                "Gives an item stack to an online player. Items that do not fit drop at their feet.",
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
        defineTool({
            name: "offline_player",
            title: "Edit saved player data",
            description:
                "Edits the saved data of an offline player, which applies at their next login. " +
                "merge_nbt merges SNBT into it. set_slot sets an inventory slot like the inventory " +
                "tool. get_player reads saved data.",
            input: {
                action: z.enum(["merge_nbt", "set_slot"]),
                player,
                snbt: z.string().optional().describe("SNBT to merge. Required for merge_nbt."),
                slot: slot.describe("Required for set_slot."),
                item: item.optional().describe("For set_slot. Leave it out to empty the slot."),
            },
            actions: {
                merge_nbt: {
                    access: "write",
                    run: (input) =>
                        required(input, "snbt").andThenAsync((request) =>
                            call(players, "mergeOfflinePlayerNbt", request).map(() =>
                                done("Saved data merged"),
                            ),
                        ),
                },
                set_slot: {
                    access: "write",
                    run: (input) =>
                        required(input, "slot").andThenAsync((request) =>
                            call(players, "setOfflineInventorySlot", request).map(() =>
                                done(`Slot ${request.slot} set`),
                            ),
                        ),
                },
            },
        }),
    ];
}
