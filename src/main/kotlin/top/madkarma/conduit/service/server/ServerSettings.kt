package top.madkarma.conduit.service.server

import net.minecraft.server.level.ChunkMap
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType

// The range that the game clamps the setting to when it reads server.properties.
private const val MIN_BROADCAST_PERCENT = 10
private const val MAX_BROADCAST_PERCENT = 1000

private val atLeastZero = 0..Int.MAX_VALUE
private val distances = ChunkMap.MIN_VIEW_DISTANCE..ChunkMap.MAX_VIEW_DISTANCE

/**
 * The server.properties settings that a running dedicated server applies at once and saves, the way
 * the game's management protocol changes them.
 */
internal val LIVE_SETTINGS: List<ServerSetting> =
    listOf(
        number(
            "view_distance",
            distances,
            { it.viewDistance() },
            { server, value -> server.setViewDistance(value) },
        ),
        number(
            "simulation_distance",
            distances,
            { it.simulationDistance() },
            { server, value -> server.setSimulationDistance(value) },
        ),
        number(
            "entity_broadcast_range_percentage",
            MIN_BROADCAST_PERCENT..MAX_BROADCAST_PERCENT,
            { it.entityBroadcastRangePercentage() },
            { server, value -> server.setEntityBroadcastRangePercentage(value) },
        ),
        choice(
            "difficulty",
            Difficulty.entries.associateBy { it.serializedName },
            { it.worldData.difficulty },
            { server, value -> server.setDifficulty(value) },
        ),
        choice(
            "game_mode",
            GameType.entries.associateBy { it.serializedName },
            { it.gameMode() },
            { server, value -> server.setGameMode(value) },
        ),
        flag(
            "force_game_mode",
            { it.forceGameMode() },
            { server, value -> server.setForceGameMode(value) },
        ),
        flag(
            "allow_flight",
            { it.allowFlight() },
            { server, value -> server.setAllowFlight(value) },
        ),
        number(
            "spawn_protection",
            atLeastZero,
            { it.spawnProtectionRadius() },
            { server, value -> server.setSpawnProtectionRadius(value) },
        ),
        number(
            "max_players",
            atLeastZero,
            { it.maxPlayers },
            { server, value -> server.setMaxPlayers(value) },
        ),
        number(
            "player_idle_timeout",
            atLeastZero,
            { it.playerIdleTimeout() },
            { server, value -> server.setPlayerIdleTimeout(value) },
        ),
        number(
            "pause_when_empty_seconds",
            atLeastZero,
            { it.pauseWhenEmptySeconds() },
            { server, value -> server.setPauseWhenEmptySeconds(value) },
        ),
        ServerSetting("motd", false, "any text", { it.motd }) { server, value ->
            server.setMotd(value)
        },
        number(
            "status_heartbeat_interval",
            atLeastZero,
            { it.statusHeartbeatInterval() },
            { server, value -> server.setStatusHeartbeatInterval(value) },
        ),
        flag("auto_save", { it.isAutoSave }, { server, value -> server.setAutoSave(value) }),
    ) + ACCESS_SETTINGS
