package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import top.madkarma.conduit.invalidInputMessage
import top.madkarma.conduit.parseJson

class ToolSessionTest {
    private val editTool =
        testTool(
            name = "edit",
            actions = mapOf("get" to Access.READ, "set" to Access.WRITE),
            defaultAction = "get",
            input =
                input {
                    optional("player", PLAYER)
                    optional("count", integerSchema(min = 1, default = 1))
                },
        )
    private val writeOnlyTool =
        testTool(name = "write_only", actions = mapOf("set" to Access.WRITE))
    private val tools = listOf(editTool, writeOnlyTool)

    private fun prepareMessage(session: ToolSession, name: String, arguments: String): String =
        invalidInputMessage {
            session.prepare(name, parseJson(arguments))
        }

    @Test
    fun `offers only the tools and actions the policy allows`() {
        val session = ToolSession(ToolPolicy("read only", readOnly = true), tools)

        assertEquals(setOf("edit"), session.offered.keys)
        assertEquals(setOf("get"), session.offered.getValue("edit").actions.keys)
    }

    @Test
    fun `prepare fills in defaults and picks the action`() {
        val prepared =
            ToolSession(ToolPolicy("full"), tools)
                .prepare("edit", parseJson("""{"action": "set"}"""))

        assertEquals("set", prepared.actionName)
        assertEquals(parseJson("""{"action": "set", "count": 1}"""), prepared.input.arguments)

        val called = prepared.called()
        assertEquals("edit", called.tool)
        assertEquals("set", called.action)
        assertEquals(Access.WRITE, called.access)
    }

    @Test
    fun `prepare refuses tools that are unknown or not offered`() {
        val session = ToolSession(ToolPolicy("chat", deniedTools = setOf("write_only")), tools)

        assertEquals("Unknown tool nope", prepareMessage(session, "nope", "{}"))
        assertEquals("Unknown tool write_only", prepareMessage(session, "write_only", "{}"))
    }

    @Test
    fun `prepare refuses arguments that do not match the schema`() {
        val session = ToolSession(ToolPolicy("full"), tools)

        assertEquals(
            "count: must have a minimum value of 1",
            prepareMessage(session, "edit", """{"count": 0}"""),
        )
    }

    @Test
    fun `prepare refuses actions the policy does not allow`() {
        val session = ToolSession(ToolPolicy("read only", readOnly = true), tools)

        assertEquals(
            "action: does not have a value in the enumeration [\"get\"]",
            prepareMessage(session, "edit", """{"action": "set"}"""),
        )
    }

    @Test
    fun `prepare refuses other players for a policy limited to one player`() {
        val session = ToolSession(ToolPolicy("chat", player = ALEX), tools)

        assertEquals(
            "Only the data of Alex is available",
            prepareMessage(session, "edit", """{"player": "Steve"}"""),
        )
    }
}
