package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.jsonObject
import top.madkarma.conduit.invalidInputMessage
import top.madkarma.conduit.parseJson

class OfferedToolTest {
    private val actions =
        mapOf("get" to Access.READ, "set" to Access.WRITE, "wipe" to Access.DESTRUCTIVE)

    private fun offered(definition: ToolDefinition, vararg names: String): OfferedTool {
        val offeredNames = names.ifEmpty { definition.actions.keys.toTypedArray() }
        return OfferedTool(definition, definition.actions.filterKeys { it in offeredNames })
    }

    private fun selected(tool: OfferedTool, arguments: String): String =
        tool.select(ToolInput(parseJson(arguments), ToolPolicy("test"))).first

    private fun selectMessage(tool: OfferedTool, arguments: String): String = invalidInputMessage {
        selected(tool, arguments)
    }

    @Test
    fun `a tool with one action keeps its input schema and needs no action`() {
        val tool = offered(testTool())

        assertEquals(testTool().input.toJson(), tool.schema)
        assertEquals("get", selected(tool, "{}"))
    }

    @Test
    fun `a tool with several actions puts the action argument first`() {
        val tool = offered(testTool(actions = actions, defaultAction = "get"))

        val properties = tool.schema.getValue("properties").jsonObject
        assertEquals(listOf("action", "player"), properties.keys.toList())
        assertEquals(
            parseJson("""{"type": "string", "enum": ["get", "set", "wipe"], "default": "get"}"""),
            properties["action"],
        )
        assertNull(tool.schema["required"])
    }

    @Test
    fun `the default action runs when the call names none`() {
        val tool = offered(testTool(actions = actions, defaultAction = "get"))

        assertEquals("get", selected(tool, "{}"))
        assertEquals("wipe", selected(tool, """{"action": "wipe"}"""))
        assertEquals("Unknown action nope", selectMessage(tool, """{"action": "nope"}"""))
    }

    @Test
    fun `without a default the action is required`() {
        val tool = offered(testTool(actions = actions))

        assertEquals(listOf("action"), tool.listing().inputSchema.required)
        assertEquals("Missing action", selectMessage(tool, "{}"))
    }

    @Test
    fun `the schema only lists the actions the policy allows`() {
        val tool = offered(testTool(actions = actions), "get")

        val action = tool.schema.getValue("properties").jsonObject.getValue("action")
        assertEquals(parseJson("""{"type": "string", "enum": ["get"], "default": "get"}"""), action)
        assertEquals("get", selected(tool, "{}"))
        assertEquals("Unknown action set", selectMessage(tool, """{"action": "set"}"""))
    }

    @Test
    fun `a default the policy does not allow is no default`() {
        val tool = offered(testTool(actions = actions, defaultAction = "set"), "get", "wipe")

        assertEquals("Missing action", selectMessage(tool, "{}"))
    }

    @Test
    fun `the listing marks the access of every action`() {
        val listing = offered(testTool(actions = actions, defaultAction = "get")).listing()

        assertEquals("test_tool", listing.name)
        assertEquals(false, listing.annotations?.readOnlyHint)
        assertEquals(true, listing.annotations?.destructiveHint)
        assertEquals(false, listing.annotations?.openWorldHint)
        assertEquals(
            parseJson(
                """{"top.madkarma.conduit/actions": {"get": "read", "set": "write", "wipe": "destructive"}}""",
            ),
            listing.meta,
        )
        assertEquals(listOf("action", "player"), listing.inputSchema.properties?.keys?.toList())
    }

    @Test
    fun `a tool that only reads is listed as read-only`() {
        val listing = offered(testTool(actions = actions), "get").listing()

        assertEquals(true, listing.annotations?.readOnlyHint)
        assertEquals(false, listing.annotations?.destructiveHint)
    }
}
