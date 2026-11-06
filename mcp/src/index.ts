#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/server";
import { serveStdio } from "@modelcontextprotocol/server/stdio";
import { close, connect } from "conduit-client";
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

const INSTRUCTIONS = [
    "Controls a running Minecraft server through the Conduit mod.",
    "Positions are block coordinates; y grows upwards. Dimensions default to the overworld.",
    "Use the registry tool to find valid block, item and entity ids before guessing them.",
    "Prefer the dedicated tools; run_command covers anything they do not.",
].join(" ");

const clients = connect({
    address: process.env.CONDUIT_ADDRESS || undefined,
    token: process.env.CONDUIT_TOKEN || undefined,
});
const readOnly = process.env.CONDUIT_MCP_READ_ONLY === "true";
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
        { name: "conduit", version: "1.0.0" },
        { instructions: INSTRUCTIONS },
    );

    for (const register of registrations) register(server, readOnly);
    return server;
});

// The open gRPC channel would keep the process alive after the host closes stdin.
process.stdin.on("end", () => {
    feed.close();
    close(clients);
});
