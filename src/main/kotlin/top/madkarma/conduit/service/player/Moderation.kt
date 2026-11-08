package top.madkarma.conduit.service.player

import java.util.Date
import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.players.BanListEntry
import net.minecraft.server.players.NameAndId
import net.minecraft.server.players.UserBanListEntry
import net.minecraft.server.players.UserWhiteListEntry
import top.madkarma.conduit.game.*

private const val BAN_SOURCE = "Conduit"

@Serializable
data class BanInfo(
    /** A player name, or an IP address for IP bans. */
    val target: String,
    val reason: String,
    val source: String,
    val created: String,
    /** Null for bans that never expire. */
    val expires: String?,
)

@Serializable data class BanList(val players: List<BanInfo>, val ips: List<BanInfo>)

@Serializable data class Whitelist(val enabled: Boolean, val players: List<String>)

internal fun MinecraftServer.listBans(): BanList =
    BanList(
        players = playerList.bans.entries.map { banInfo(it.displayName.string, it) },
        ips = playerList.ipBans.entries.map { banInfo(it.displayName.string, it) },
    )

/** Bans a player, also an offline one, for some minutes or for good, and kicks them if online. */
internal fun MinecraftServer.ban(ref: String, reason: String, minutes: Int?) {
    val profile = profile(ref)
    val expires = minutes?.let { Date(System.currentTimeMillis() + it * 60_000L) }

    playerList.bans.add(UserBanListEntry(profile, null, BAN_SOURCE, expires, reason))
    playerList
        .getPlayer(profile.id())
        ?.connection
        ?.disconnect(Component.translatable("multiplayer.disconnect.banned"))
}

internal fun MinecraftServer.pardon(ref: String) {
    val profile = profile(ref)
    if (!playerList.bans.isBanned(profile)) failedPrecondition("${profile.name()} is not banned")
    playerList.bans.remove(profile)
}

internal fun MinecraftServer.listWhitelist(): Whitelist =
    Whitelist(isUsingWhitelist, playerList.whiteListNames.sorted())

internal fun MinecraftServer.addToWhitelist(ref: String) {
    playerList.whiteList.add(UserWhiteListEntry(profile(ref)))
}

/** Removes a player from the whitelist, which kicks them when the server enforces it. */
internal fun MinecraftServer.removeFromWhitelist(ref: String) {
    playerList.whiteList.remove(profile(ref))
    kickUnlistedPlayers()
}

internal fun MinecraftServer.changeWhitelistEnabled(enabled: Boolean) {
    setUsingWhitelist(enabled)
    kickUnlistedPlayers()
}

private fun MinecraftServer.profile(ref: String): NameAndId {
    val id = resolvePlayerId(ref)
    return NameAndId(id, lastKnownName(id).ifEmpty { ref })
}

private fun banInfo(target: String, entry: BanListEntry<*>) =
    BanInfo(
        target = target,
        reason = entry.reason.orEmpty(),
        source = entry.source.orEmpty(),
        created = entry.created.toInstant().toString(),
        expires = entry.expires?.toInstant()?.toString(),
    )
