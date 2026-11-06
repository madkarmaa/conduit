#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/server";
import { serveStdio } from "@modelcontextprotocol/server/stdio";
import { close, connect } from "@conduit/client";
import packageJson from "../package.json" with { type: "json" };
import { EventFeed } from "./events.ts";
import { batchTools } from "./tools/batch.ts";
import { blockTools } from "./tools/blocks.ts";
import { entityEditTools } from "./tools/entity-edits.ts";
import { entityMovementTools } from "./tools/entity-movement.ts";
import { entityTools } from "./tools/entities.ts";
import { environmentTools } from "./tools/environment.ts";
import { eventTools } from "./tools/events.ts";
import { inventoryTools } from "./tools/inventory.ts";
import { playerActionTools } from "./tools/player-actions.ts";
import { playerTools } from "./tools/players.ts";
import { regionTools } from "./tools/regions.ts";
import { registryTools } from "./tools/registry.ts";
import { serverTools } from "./tools/server.ts";
import { structureTools } from "./tools/structures.ts";
import { worldTools } from "./tools/world.ts";
import type { ToolPolicy } from "./policy.ts";

const INSTRUCTIONS = [
    "Controls a running Minecraft server through the Conduit mod.",
    "Positions are block coordinates, and y grows upwards. Dimensions default to the overworld.",
    "Use the registry tool to look up valid block, item and entity ids instead of guessing them.",
    "Prefer the dedicated tools. run_command covers anything they do not.",
].join(" ");

const clients = connect({
    address: process.env.CONDUIT_ADDRESS || undefined,
    token: process.env.CONDUIT_TOKEN || undefined,
});
const policy: ToolPolicy = {
    readOnly: process.env.CONDUIT_MCP_READ_ONLY === "true",
    allowedTools: list(process.env.CONDUIT_MCP_TOOLS),
    deniedTools: list(process.env.CONDUIT_MCP_DENY_TOOLS),
    player: list(process.env.CONDUIT_MCP_PLAYER),
};
const feed = new EventFeed(clients.events);

const registrations = [
    ...serverTools(clients),
    ...blockTools(clients),
    ...regionTools(clients),
    ...worldTools(clients),
    ...environmentTools(clients),
    ...entityTools(clients),
    ...entityMovementTools(clients),
    ...entityEditTools(clients),
    ...playerTools(clients),
    ...inventoryTools(clients),
    ...playerActionTools(clients),
    ...registryTools(clients),
    ...structureTools(clients),
    ...batchTools(clients),
    ...eventTools(feed),
];

serveStdio(() => {
    const server = new McpServer(
        { name: "conduit", version: packageJson.version },
        { instructions: instructions(policy) },
    );

    for (const register of registrations) register(server, policy);
    return server;
});

function list(value: string | undefined): string[] {
    return (value ?? "")
        .split(",")
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

function instructions({ player }: ToolPolicy): string {
    if (player.length === 0) return INSTRUCTIONS;
    return `${INSTRUCTIONS} The tools only accept and return data about the player ${player[0]}.`;
}

// The open gRPC channel would keep the process alive after the host closes stdin.
process.stdin.on("end", () => {
    feed.close();
    close(clients);
});
