package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.parseJson

class ToolCallTest {
    private val noArguments = JsonObject(emptyMap())

    private fun call(
        policy: ToolPolicy,
        tool: ToolDefinition,
        arguments: JsonObject = noArguments,
    ) = runBlocking { ToolSession(policy, listOf(tool)).call(tool.name, arguments) }

    private fun text(result: CallToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }

    @Test
    fun `call runs the action and replies with its text`() {
        val tool = testTool(actions = mapOf("get" to Access.READ, "set" to Access.WRITE))

        val result = call(ToolPolicy("full"), tool, parseJson("""{"action": "set"}"""))

        assertEquals("set", text(result))
        assertFalse(result.isError == true)
    }

    @Test
    fun `call turns expected failures into error results`() {
        val result = runBlocking {
            ToolSession(ToolPolicy("full"), emptyList()).call("nope", noArguments)
        }

        assertEquals("Invalid input: Unknown tool nope", text(result))
        assertEquals(true, result.isError)
    }

    @Test
    fun `call asks the approval gate before it runs an action`() {
        var ran = false
        val asked = mutableListOf<CalledAction>()
        val stop =
            singleActionTool(
                "stop",
                AsyncAction(Access.DESTRUCTIVE) { _, _ -> Reply("stopped").also { ran = true } },
            )
        val gate = ApprovalGate { calls ->
            asked += calls
            throw ConduitError.NotApproved("Alex denied this.")
        }

        val result = call(ToolPolicy("full", approvals = gate), stop)

        assertEquals("Not approved: Alex denied this.", text(result))
        assertEquals(listOf("stop.stop"), asked.map { "${it.tool}.${it.action}" })
        assertFalse(ran)
    }

    @Test
    fun `call redacts the reply for a policy limited to one player`() {
        val data = parseJson("""{"name": "Alex", "ipAddress": "10.0.0.1"}""")
        val who =
            singleActionTool(
                "who",
                AsyncAction(Access.READ) { _, _ -> Reply(data.toString(), data) },
            )

        val result = call(ToolPolicy("chat", player = ALEX), who)

        assertEquals("""{"name":"Alex"}""", text(result))
        assertEquals(parseJson("""{"name": "Alex"}"""), result.structuredContent)
    }

    @Test
    fun `call of a game action without a running server says so`() {
        val time = singleActionTool("time", readAction { _, _ -> Reply("noon") })

        assertEquals(
            "Unavailable: No Minecraft server is running. Open a world first.",
            text(call(ToolPolicy("full"), time)),
        )
    }
}
