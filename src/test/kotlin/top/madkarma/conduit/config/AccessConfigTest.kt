package top.madkarma.conduit.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.madkarma.conduit.parseJson

class AccessConfigTest {
    private val alex = AccessEntry("Alex", "00000000-0000-0000-0000-000000000001")
    private val steve = AccessEntry("Steve", "00000000-0000-0000-0000-000000000002")

    @Test
    fun `the owner matches by exact name in either server mode, or by UUID`() {
        val onlineId = "4b1a6c2e-1f0d-4e7a-9d3c-2a5b8e6f0c11"
        val offlineId = "a6e5ff8c-4bce-3a8e-a8b5-0f0f8c2a3b4d"
        val byName = AccessConfig(owner = "Alex")
        val byId = AccessConfig(owner = onlineId.uppercase())

        assertTrue(byName.isOwner("Alex", onlineId))
        assertTrue(byName.isOwner("Alex", offlineId))
        assertFalse(byName.isOwner("alex", offlineId))
        assertTrue(byId.isOwner("Alex2", onlineId))
        assertFalse(byId.isOwner("Alex", offlineId))
        assertFalse(AccessConfig().isOwner("Alex", onlineId))
    }

    @Test
    fun `allowing and denying move a player between the lists`() {
        val denied = ModeAccess(allow = listOf(steve), deny = listOf(alex))

        val allowed = denied.allowing(alex.copy(name = "Alex2"))
        assertEquals(listOf(steve, alex.copy(name = "Alex2")), allowed.allow)
        assertEquals(emptyList(), allowed.deny)

        assertEquals(ModeAccess(allow = listOf(steve), deny = listOf(alex)), allowed.denying(alex))
    }

    @Test
    fun `a change keeps the rest of the file as written`() {
        val written =
            parseJson(
                """{"mcp": {"port": 25586}, "access": {"operators": false, "full": {"allow": []}}}""",
            )

        val changed = written.withModeAccess("chat") { it.allowing(alex) }

        assertEquals(
            parseJson(
                """{"mcp": {"port": 25586}, "access": {"operators": false, "full": {"allow": []},
                "chat": {"allow": [{"name": "Alex", "uuid": "${alex.uuid}"}], "deny": []}}}""",
            ),
            changed,
        )
    }

    @Test
    fun `a disabled chat agent is in use while it has allowed players`() {
        val disabled =
            VALID_CONFIG.copy(
                agents =
                    VALID_CONFIG.agents.copy(chat = VALID_CONFIG.agents.chat.copy(enabled = false)),
            )
        val allowed =
            disabled.copy(
                access = AccessConfig(chat = ModeAccess(allow = listOf(alex))),
            )

        assertFalse(disabled.uses("agents.chat.runner.secret"))
        assertTrue(allowed.uses("agents.chat.runner.secret"))
        assertEquals(setOf("full", "chat"), allowed.agentsInUse().keys)
    }

    @Test
    fun `access entries need a UUID`() {
        val config =
            VALID_CONFIG.copy(
                access =
                    AccessConfig(full = ModeAccess(deny = listOf(AccessEntry("Alex", "Alex")))),
            )

        assertEquals(
            listOf("access.full.deny[0].uuid must be a UUID"),
            config.problems(TOOL_ACTIONS),
        )
    }
}
