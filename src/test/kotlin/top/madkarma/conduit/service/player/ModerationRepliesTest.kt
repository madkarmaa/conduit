package top.madkarma.conduit.service.player

import kotlin.test.Test
import kotlin.test.assertEquals

class ModerationRepliesTest {
    @Test
    fun `the whitelist shows each entry's UUID and why a stale one never matches`() {
        val whitelist =
            Whitelist(
                enabled = true,
                players =
                    listOf(
                        ListedPlayer("Alex", "6b1f0d5e-0000-3000-8000-000000000001"),
                        ListedPlayer("Steve", "37ef978f-17e3-4742-aa5c-c979f0326d53", "stale"),
                    ),
            )

        assertEquals(
            listOf(
                "Whitelist on, 2 players",
                "Alex 6b1f0d5e-0000-3000-8000-000000000001",
                "Steve 37ef978f-17e3-4742-aa5c-c979f0326d53, stale",
            ),
            whitelistReply(whitelist).text.lines(),
        )
    }

    @Test
    fun `a checked whitelist says that entries without a note match`() {
        val whitelist = Whitelist(true, listOf(ListedPlayer("Alex", "uuid-1")), checked = true)

        assertEquals(
            "Whitelist on, 1 players. Entries without a note hold the offline UUID of their name",
            whitelistReply(whitelist).text.lines().first(),
        )
    }

    @Test
    fun `player bans show their UUID and IP bans do not`() {
        val player =
            BanInfo("Alex", "uuid-1", null, "griefing", "Conduit", "2026-11-10T12:00:00Z", null)
        val address =
            BanInfo("10.0.0.1", null, null, "spam", "Conduit", "2026-11-10T12:00:00Z", null)

        assertEquals(
            listOf(
                "1 player bans, 1 IP bans",
                "Alex uuid-1: griefing, by Conduit until forever",
                "10.0.0.1: spam, by Conduit until forever",
            ),
            bansReply(BanList(listOf(player), listOf(address))).text.lines(),
        )
    }
}
