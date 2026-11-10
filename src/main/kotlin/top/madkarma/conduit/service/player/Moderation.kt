package top.madkarma.conduit.service.player

import java.util.Date
import kotlinx.serialization.Serializable
import net.minecraft.core.UUIDUtil
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
    /** The banned UUID, null for IP bans. */
    val uuid: String? = null,
    /** Why no joining player matches the ban, when none does. */
    val note: String? = null,
    val reason: String,
    val source: String,
    val created: String,
    /** Null for bans that never expire. */
    val expires: String?,
)

@Serializable data class BanList(val players: List<BanInfo>, val ips: List<BanInfo>)

@Serializable
data class Whitelist(
    val enabled: Boolean,
    val players: List<ListedPlayer>,
    /** Whether each entry was checked against the offline UUID of its name. */
    val checked: Boolean = false,
)

/** A player in the whitelist or the bans, by the UUID the entry holds. */
@Serializable
data class ListedPlayer(
    val name: String,
    val uuid: String,
    /** Why no joining player matches the entry, when none does. */
    val note: String? = null,
)

internal fun MinecraftServer.listBans(): BanList =
    BanList(
        players =
            playerList.bans.entries.map { entry ->
                val user = entry.user
                banInfo(entry.displayName.string, entry)
                    .copy(uuid = user?.id()?.toString(), note = user?.let(::unmatchedNote))
            },
        ips = playerList.ipBans.entries.map { banInfo(it.displayName.string, it) },
    )

/**
 * Bans a player, also an offline one, for some minutes or for good, and kicks them if online.
 * Returns the banned name and UUID.
 */
internal fun MinecraftServer.ban(ref: String, reason: String, minutes: Int?): NameAndId {
    val profile = profile(ref)
    val expires = minutes?.let { Date(System.currentTimeMillis() + it * 60_000L) }

    playerList.bans.add(UserBanListEntry(profile, null, BAN_SOURCE, expires, reason))
    playerList
        .getPlayer(profile.id())
        ?.connection
        ?.disconnect(Component.translatable("multiplayer.disconnect.banned"))
    return profile
}

internal fun MinecraftServer.pardon(ref: String) {
    val profile = profile(ref)
    if (!playerList.bans.isBanned(profile)) failedPrecondition("${profile.name()} is not banned")
    playerList.bans.remove(profile)
}

internal fun MinecraftServer.listWhitelist(): Whitelist =
    Whitelist(
        isUsingWhitelist,
        playerList.whiteList.entries
            .mapNotNull { it.user }
            .sortedBy { it.name().lowercase() }
            .map { ListedPlayer(it.name(), it.id().toString(), unmatchedNote(it)) },
        joinsWithOfflineIds(),
    )

/** Adds a player and returns the name and UUID that the entry holds. */
internal fun MinecraftServer.addToWhitelist(ref: String): NameAndId {
    val profile = profile(ref)
    playerList.whiteList.add(UserWhiteListEntry(profile))
    return profile
}

/**
 * Removes a player from the whitelist, which kicks them when the server enforces it. A UUID removes
 * that exact entry.
 */
internal fun MinecraftServer.removeFromWhitelist(ref: String): NameAndId {
    val profile = profile(ref)
    if (!playerList.whiteList.isWhiteListed(profile)) {
        failedPrecondition("${describe(profile)} is not on the whitelist")
    }
    playerList.whiteList.remove(profile)
    kickUnlistedPlayers()
    return profile
}

/** The name and UUID, or the UUID alone for an entry added by a UUID whose name is unknown. */
internal fun describe(profile: NameAndId): String =
    if (profile.hasName()) "${profile.name()} (${profile.id()})" else "${profile.id()}"

// Such entries hold the UUID as their name, since the game needs one.
internal fun NameAndId.hasName(): Boolean = name() != id().toString()

// Entries that name players by an online UUID, which players joining an offline server never have,
// such as the ones that the vanilla commands add after looking the name up online.
private fun MinecraftServer.unmatchedNote(profile: NameAndId): String? {
    if (!joinsWithOfflineIds() || !profile.hasName()) return null
    if (profile.id() == UUIDUtil.createOfflinePlayerUUID(profile.name())) return null
    return "not the offline UUID of ${profile.name()}, so no player joining this offline server " +
        "has it"
}

private fun MinecraftServer.profile(ref: String): NameAndId {
    val id = resolvePlayerId(ref)
    return NameAndId(id, lastKnownName(id).ifEmpty { ref })
}

private fun banInfo(target: String, entry: BanListEntry<*>) =
    BanInfo(
        target = target,
        reason = entry.reason.orEmpty(),
        source = entry.source,
        created = entry.created.toInstant().toString(),
        expires = entry.expires?.toInstant()?.toString(),
    )
