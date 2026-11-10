package top.madkarma.conduit.bot

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.madkarma.conduit.config.AccessConfig
import top.madkarma.conduit.config.AccessEntry
import top.madkarma.conduit.config.ChatToolsConfig
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.ModeAccess
import top.madkarma.conduit.config.ToolsConfig
import top.madkarma.conduit.mcp.ApprovalGate

class AccessTest {
    private val alex = Asker("Alex", UUID(0, 1))
    private val gate = ApprovalGate {}

    private val alexId = alex.uuid.toString()
    private val steveId = UUID(0, 2).toString()
    private val onlyAlex = listOf(AccessEntry("Alex", alexId))

    private fun chatPolicy(chat: ChatToolsConfig) =
        ConduitConfig(tools = ToolsConfig(chat = chat)).toolPolicy(Mode.CHAT, alex, gate)

    private fun ConduitConfig.withAgents(full: Boolean = true, chat: Boolean = true) =
        copy(
            agents =
                agents.copy(
                    full = agents.full.copy(enabled = full),
                    chat = agents.chat.copy(enabled = chat),
                ),
        )

    @Test
    fun `operators and allowed players get full mode`() {
        val allowed = ConduitConfig(access = AccessConfig(full = ModeAccess(allow = onlyAlex)))

        assertEquals(Mode.FULL, ConduitConfig().modeFor(alexId, Role.OPERATOR))
        assertEquals(Mode.CHAT, ConduitConfig().modeFor(alexId, Role.PLAYER))
        assertEquals(Mode.FULL, allowed.modeFor(alexId, Role.PLAYER))
        assertEquals(Mode.CHAT, allowed.modeFor(steveId, Role.PLAYER))
    }

    @Test
    fun `a denied operator and operators the config leaves out get chat mode`() {
        val denied = ConduitConfig(access = AccessConfig(full = ModeAccess(deny = onlyAlex)))
        val noOperators = ConduitConfig(access = AccessConfig(operators = false))

        assertEquals(Mode.CHAT, denied.modeFor(alexId, Role.OPERATOR))
        assertEquals(Mode.CHAT, noOperators.modeFor(alexId, Role.OPERATOR))
    }

    @Test
    fun `the owner gets full mode even when denied, without operators and without being one`() {
        val owner =
            ConduitConfig(
                access =
                    AccessConfig(
                        operators = false,
                        full = ModeAccess(deny = onlyAlex),
                        owner = "Alex",
                    ),
            )

        assertEquals(Mode.FULL, owner.modeFor(alexId, Role.OWNER))
        assertEquals(Mode.CHAT, owner.withAgents(full = false).modeFor(alexId, Role.OWNER))
    }

    @Test
    fun `a disabled full mode answers nobody, not even allowed players`() {
        val config =
            ConduitConfig(access = AccessConfig(full = ModeAccess(allow = onlyAlex)))
                .withAgents(full = false)

        assertEquals(Mode.CHAT, config.modeFor(alexId, Role.OPERATOR))
    }

    @Test
    fun `an enabled chat mode answers everyone but the denied players`() {
        val config = ConduitConfig(access = AccessConfig(chat = ModeAccess(deny = onlyAlex)))

        assertNull(config.modeFor(alexId, Role.PLAYER))
        assertEquals(Mode.CHAT, config.modeFor(steveId, Role.PLAYER))
    }

    @Test
    fun `a disabled chat mode answers only the allowed players`() {
        val config =
            ConduitConfig(access = AccessConfig(chat = ModeAccess(allow = onlyAlex)))
                .withAgents(chat = false)

        assertEquals(Mode.CHAT, config.modeFor(alexId, Role.PLAYER))
        assertNull(config.modeFor(steveId, Role.PLAYER))
    }

    @Test
    fun `full mode may use every tool`() {
        val policy = ConduitConfig().toolPolicy(Mode.FULL, alex, gate)

        assertEquals("Alex in full mode", policy.name)
        assertFalse(policy.readOnly)
        assertNull(policy.allowedTools)
        assertNull(policy.player)
        assertSame(gate, policy.approvals)
    }

    @Test
    fun `full mode only reads when the config says so`() {
        val config = ConduitConfig(tools = ToolsConfig(readOnly = true))

        assertTrue(config.toolPolicy(Mode.FULL, alex, gate).readOnly)
    }

    @Test
    fun `chat mode reads the default tools as the asking player`() {
        val policy = ConduitConfig().toolPolicy(Mode.CHAT, alex, gate)

        assertEquals("Alex in chat mode", policy.name)
        assertTrue(policy.readOnly)
        assertEquals("Alex", policy.player?.name)
        assertEquals("00000000-0000-0000-0000-000000000001", policy.player?.uuid)
        assertEquals(
            setOf(
                "server_info",
                "world_info",
                "get_blocks",
                "structures",
                "entities",
                "get_player",
                "inventory",
                "game_data",
            ),
            policy.allowedTools,
        )
        assertSame(gate, policy.approvals)
    }

    @Test
    fun `a chat deny list on its own allows every other tool`() {
        val policy = chatPolicy(ChatToolsConfig(deny = listOf("find")))

        assertNull(policy.allowedTools)
        assertEquals(setOf("find"), policy.deniedTools)
    }

    @Test
    fun `a chat allow list replaces the default tools`() {
        val policy = chatPolicy(ChatToolsConfig(allow = listOf("find"), deny = listOf("entities")))

        assertEquals(setOf("find"), policy.allowedTools)
        assertEquals(setOf("entities"), policy.deniedTools)
    }

    @Test
    fun `each mode has its own agent`() {
        val config = ConduitConfig()

        assertSame(config.agents.full, config.agent(Mode.FULL))
        assertSame(config.agents.chat, config.agent(Mode.CHAT))
    }
}
