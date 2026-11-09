package top.madkarma.conduit.service.server

import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel

private const val MAX_PERMISSION_LEVEL = 4

// The game kicks the players who are not on the whitelist once it applies to them.
internal val ACCESS_SETTINGS: List<ServerSetting> =
    listOf(
        flag(
            "whitelist",
            { it.isUsingWhitelist },
            { server, value ->
                server.isUsingWhitelist = value
                server.kickUnlistedPlayers()
            },
            access = true,
        ),
        flag(
            "enforce_whitelist",
            { it.isEnforceWhitelist },
            { server, value ->
                server.isEnforceWhitelist = value
                server.kickUnlistedPlayers()
            },
            access = true,
        ),
        number(
            "operator_permission_level",
            0..MAX_PERMISSION_LEVEL,
            { it.operatorUserPermissions().level().id() },
            { server, value ->
                server.setOperatorUserPermissions(
                    LevelBasedPermissionSet.forLevel(PermissionLevel.byId(value)),
                )
            },
            access = true,
        ),
        flag(
            "hide_online_players",
            { it.hidesOnlinePlayers() },
            { server, value -> server.setHidesOnlinePlayers(value) },
            access = true,
        ),
        flag(
            "replies_to_status",
            { it.repliesToStatus() },
            { server, value -> server.setRepliesToStatus(value) },
            access = true,
        ),
        flag(
            "accepts_transfers",
            { it.acceptsTransfers() },
            { server, value -> server.setAcceptsTransfers(value) },
            access = true,
        ),
    )
