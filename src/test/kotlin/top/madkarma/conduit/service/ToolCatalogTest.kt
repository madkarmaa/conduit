package top.madkarma.conduit.service

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.bot.Asker
import top.madkarma.conduit.bot.Mode
import top.madkarma.conduit.bot.toolPolicy
import top.madkarma.conduit.config.VALID_CONFIG
import top.madkarma.conduit.config.problems
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.mcp.Access
import top.madkarma.conduit.mcp.OfferedTool
import top.madkarma.conduit.mcp.ToolSession
import top.madkarma.conduit.mcp.validateArguments

class ToolCatalogTest {
    private val chatPolicy = VALID_CONFIG.toolPolicy(Mode.CHAT, Asker("Alex", UUID(0, 1)), {})

    @Test
    fun `tool names are unique`() {
        val names = ALL_TOOLS.map { it.name }

        assertEquals(names.distinct(), names)
    }

    @Test
    fun `every default action is one of the tool's actions`() {
        for (tool in ALL_TOOLS) {
            val default = tool.defaultAction ?: continue
            assertTrue(default in tool.actions, "${tool.name} defaults to $default")
        }
    }

    @Test
    fun `every schema describes its required fields and validates`() {
        for (tool in ALL_TOOLS) {
            val schema = OfferedTool(tool, tool.actions).schema
            val properties = schema.getValue("properties") as JsonObject
            val required =
                (schema["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
            assertTrue(properties.keys.containsAll(required), "${tool.name} requires $required")

            try {
                validateArguments(schema, JsonObject(emptyMap()))
            } catch (_: ConduitError.InvalidInput) {
                // Tools with required fields refuse empty arguments, which still shows the schema
                // works.
            }
        }
    }

    @Test
    fun `every action that needs approval is one of the tool's actions`() {
        for (tool in ALL_TOOLS) {
            assertTrue(tool.actions.keys.containsAll(tool.needApproval), tool.name)
        }
    }

    @Test
    fun `the default config fits the catalog`() {
        val toolActions = ALL_TOOLS.associate { it.name to it.actions.keys }

        assertEquals(emptyList(), VALID_CONFIG.problems(toolActions))
    }

    @Test
    fun `chat mode offers its default tools and only actions that read`() {
        val session = ToolSession(chatPolicy, ALL_TOOLS)

        assertEquals(chatPolicy.allowedTools, session.offered.keys)
        assertTrue(session.offered.values.none { it.definition.seesOtherPlayers })
        assertTrue(
            session.offered.values.all { tool ->
                tool.actions.values.all { it.access == Access.READ }
            },
        )
        assertEquals(setOf("read"), session.offered.getValue("inventory").actions.keys)
    }
}
