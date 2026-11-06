import type { ConduitClients } from "conduit-client";
import { z } from "zod";
import { compact, lines } from "../format.ts";
import { call } from "../grpc.ts";
import { defineTool } from "../tool.ts";

type BatchRequest = Parameters<ConduitClients["batch"]["execute"]>[0];

const operation = z
    .record(z.string(), z.record(z.string(), z.unknown()))
    .describe(
        'One operation, e.g. {"setBlock": {"pos": {"x": 0, "y": 64, "z": 0}, "state": "minecraft:stone"}}.',
    );

export function batchTools({ batch }: ConduitClients) {
    return [
        defineTool({
            name: "batch",
            title: "Batch",
            description:
                "Runs many operations in order within one server tick. Each operation is an object " +
                "with a single key naming the gRPC method in camelCase and its request with " +
                "camelCase fields. Methods: getBlock, getBlocks, setBlock, setBlocks, fill, " +
                "breakBlock, getBlockEntity, setBlockEntity, getBiome, getHeight, getChunk, " +
                "setChunkForced, getTime, setTime, getWeather, setWeather, getGameRules, " +
                "setGameRule, getSpawn, setSpawn, explode, getWorldBorder, setWorldBorder, " +
                "listEntities, getEntity, spawnEntity, removeEntity, teleport, setVelocity, " +
                "setRotation, getNbt, mergeNbt, setCustomName, setTags, damage, setHealth, " +
                "getEffects, addEffect, removeEffect, getAttributes, setAttributeBase, mount, " +
                "dismount, listPlayers, getPlayer, getInventory, setInventorySlot, giveItem, " +
                "clearInventory, setSelectedSlot, getEnderChest, setGameMode, setFood, " +
                "setExperience, sendMessage, sendTitle, playSound, kick, setOperator, " +
                'executeCommand, broadcast. Text fields take {"plain": "..."}.',
            input: {
                operations: z.array(operation).min(1),
                stop_on_error: z
                    .boolean()
                    .default(false)
                    .describe("Skip the rest after a failure."),
            },
            actions: {
                run: {
                    access: "destructive",
                    run: (input) => {
                        // The server validates every operation and reports unknown ones per result.
                        const request = {
                            operations: input.operations,
                            stopOnError: input.stop_on_error,
                        } as BatchRequest;
                        return call(batch, "execute", request).map((response) =>
                            lines(
                                `${response.results.length} results`,
                                response.results.map((result, index) =>
                                    JSON.stringify(compact({ index, ...result })),
                                ),
                                { results: response.results.map(compact) },
                            ),
                        );
                    },
                },
            },
        }),
    ];
}
