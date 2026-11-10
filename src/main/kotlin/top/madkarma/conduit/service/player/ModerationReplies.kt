package top.madkarma.conduit.service.player

import top.madkarma.conduit.mcp.Reply
import top.madkarma.conduit.mcp.encodedObject
import top.madkarma.conduit.mcp.lines

internal fun bansReply(bans: BanList): Reply {
    val rows =
        (bans.players + bans.ips).map {
            val uuid = it.uuid?.let { uuid -> " $uuid" }.orEmpty()
            val note = it.note?.let { note -> ", $note" }.orEmpty()
            "${it.target}$uuid: ${it.reason}, by ${it.source} until ${it.expires ?: "forever"}$note"
        }
    return lines(
        "${bans.players.size} player bans, ${bans.ips.size} IP bans",
        rows,
        encodedObject(bans),
    )
}

internal fun whitelistReply(whitelist: Whitelist): Reply {
    val state = if (whitelist.enabled) "on" else "off"
    // Without this, agents cannot tell a checked entry from one nobody looked at.
    val checked =
        if (whitelist.checked) ". Entries without a note hold the offline UUID of their name"
        else ""
    return lines(
        "Whitelist $state, ${whitelist.players.size} players$checked",
        whitelist.players.map(::describeListed),
        encodedObject(whitelist),
    )
}

private fun describeListed(player: ListedPlayer): String =
    "${player.name} ${player.uuid}" + player.note?.let { ", $it" }.orEmpty()
