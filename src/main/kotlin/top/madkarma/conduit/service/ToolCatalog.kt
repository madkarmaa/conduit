package top.madkarma.conduit.service

import top.madkarma.conduit.mcp.BATCH
import top.madkarma.conduit.mcp.ToolDefinition
import top.madkarma.conduit.service.entity.*
import top.madkarma.conduit.service.events.EVENTS
import top.madkarma.conduit.service.player.*
import top.madkarma.conduit.service.registry.GAME_DATA
import top.madkarma.conduit.service.server.DATAPACKS
import top.madkarma.conduit.service.server.RUN_COMMAND
import top.madkarma.conduit.service.server.SERVER_ADMIN
import top.madkarma.conduit.service.server.SERVER_INFO
import top.madkarma.conduit.service.structure.STRUCTURES
import top.madkarma.conduit.service.world.*

/** Every tool, in the order clients list them. */
val ALL_TOOLS: List<ToolDefinition> =
    listOf(
        SERVER_INFO,
        SERVER_ADMIN,
        RUN_COMMAND,
        DATAPACKS,
        WORLD_INFO,
        EDIT_WORLD,
        GET_BLOCKS,
        EDIT_BLOCKS,
        STRUCTURES,
        ENTITIES,
        EDIT_ENTITY,
        MOVE_ENTITY,
        LIST_PLAYERS,
        GET_PLAYER,
        EDIT_PLAYER,
        MODERATION,
        INVENTORY,
        NOTIFY,
        GAME_DATA,
        EVENTS,
        BATCH,
    )
