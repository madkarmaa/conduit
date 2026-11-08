package top.madkarma.conduit.bot

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.madkarma.conduit.config.ChatToolsConfig
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.ToolsConfig
import top.madkarma.conduit.mcp.ApprovalGate

class AccessTest {
    private val alex = Asker("Alex", UUID(0, 1))
    private val gate = ApprovalGate {}

    private fun chatPolicy(chat: ChatToolsConfig) =
        ConduitConfig(tools = ToolsConfig(chat = chat)).toolPolicy(Mode.CHAT, alex, gate)

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
