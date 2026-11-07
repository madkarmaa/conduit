package top.madkarma.conduit.service

import top.madkarma.conduit.mcp.BATCH
import top.madkarma.conduit.mcp.ToolDefinition
import top.madkarma.conduit.service.entity.*
import top.madkarma.conduit.service.events.WAIT_FOR_EVENTS
import top.madkarma.conduit.service.player.*
import top.madkarma.conduit.service.registry.RECIPES
import top.madkarma.conduit.service.registry.REGISTRY
import top.madkarma.conduit.service.server.RUN_COMMAND
import top.madkarma.conduit.service.server.SERVER_ADMIN
import top.madkarma.conduit.service.server.SERVER_INFO
import top.madkarma.conduit.service.structure.STRUCTURES
import top.madkarma.conduit.service.world.*

/** Every tool, in the order clients list them. */
val ALL_TOOLS: List<ToolDefinition> =
    listOf(
        SERVER_INFO,
        RUN_COMMAND,
        SERVER_ADMIN,
        GET_BLOCK,
        EDIT_BLOCKS,
        GET_REGION,
        TERRAIN,
        WORLD_INFO,
        WORLD_SETTINGS,
        GAME_RULES,
        WORLD_BORDER,
        EXPLODE,
        LIST_ENTITIES,
        GET_ENTITY,
        SPAWN_ENTITY,
        REMOVE_ENTITY,
        MOVE_ENTITY,
        EDIT_ENTITY,
        LIST_PLAYERS,
        GET_PLAYER,
        INVENTORY,
        GIVE_ITEM,
        OFFLINE_PLAYER,
        EDIT_PLAYER,
        NOTIFY,
        REGISTRY,
        RECIPES,
        STRUCTURES,
        BATCH,
        WAIT_FOR_EVENTS,
    )
