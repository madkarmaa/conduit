package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.madkarma.conduit.invalidInputMessage
import top.madkarma.conduit.parseJson

class ToolPolicyTest {
    private val limited = ToolPolicy("Alex in chat mode", player = ALEX)

    private fun checkPlayersMessage(policy: ToolPolicy, arguments: String): String =
        invalidInputMessage {
            policy.checkPlayers(parseJson(arguments))
        }

    @Test
    fun `offers every tool without lists`() {
        assertTrue(ToolPolicy("full").offers(testTool()))
    }

    @Test
    fun `offers only allowed tools that are not denied`() {
        val policy =
            ToolPolicy("chat", allowedTools = setOf("allowed", "both"), deniedTools = setOf("both"))

        assertTrue(policy.offers(testTool("allowed")))
        assertFalse(policy.offers(testTool("both")))
        assertFalse(policy.offers(testTool("other")))
        assertFalse(ToolPolicy("chat", deniedTools = setOf("denied")).offers(testTool("denied")))
    }

    @Test
    fun `a policy limited to one player hides tools that see other players`() {
        val seeing = testTool(seesOtherPlayers = true)

        assertFalse(limited.offers(seeing))
        assertTrue(ToolPolicy("full").offers(seeing))
    }

    @Test
    fun `a read-only policy allows only actions that read`() {
        val readOnly = ToolPolicy("read only", readOnly = true)
        val read = AsyncAction(Access.READ) { _, _ -> Reply("") }
        val write = AsyncAction(Access.WRITE) { _, _ -> Reply("") }

        assertTrue(readOnly.allows(read))
        assertFalse(readOnly.allows(write))
        assertTrue(ToolPolicy("full").allows(write))
    }

    @Test
    fun `checkPlayers accepts any player without a player limit`() {
        ToolPolicy("full").checkPlayers(parseJson("""{"player": "Steve", "players": []}"""))
    }

    @Test
    fun `checkPlayers accepts the policy's player by name or UUID in any case`() {
        limited.checkPlayers(parseJson("""{"player": "alex"}"""))
        limited.checkPlayers(parseJson("""{"players": ["ALEX", "${ALEX.uuid}"]}"""))
        limited.checkPlayers(parseJson("""{"other": "Steve"}"""))
    }

    @Test
    fun `checkPlayers refuses other players`() {
        assertEquals(
            "Only the data of Alex is available",
            checkPlayersMessage(limited, """{"player": "Steve"}"""),
        )
        assertEquals(
            "Only the data of Alex is available",
            checkPlayersMessage(limited, """{"players": ["Alex", "Steve"]}"""),
        )
        assertEquals(
            "Only the data of Alex is available",
            checkPlayersMessage(limited, """{"players": [{"name": "Alex"}]}"""),
        )
    }

    @Test
    fun `checkPlayers refuses an empty players filter, which means everyone`() {
        assertEquals("Set players to Alex", checkPlayersMessage(limited, """{"players": []}"""))
    }
}
