package top.madkarma.conduit.bot

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import top.madkarma.conduit.mcp.Access
import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.CalledAction
import top.madkarma.conduit.mcp.RecordedCall

class RequestProgressTest {
    private val noArguments = JsonObject(emptyMap())
    private val request = BotRequest(Asker("Alex", UUID(0, 1)), "build a house", Mode.FULL)
    private val progress = RequestProgress(request, null)

    private fun finish(tool: String, action: String, access: Access, failed: Boolean = false) =
        progress.finished(
            RecordedCall(
                tool,
                CalledAction(tool, action, access, noArguments),
                noArguments,
                failed,
                "",
            ),
        )

    @Test
    fun `the action bar shows the current call and the open approval`() {
        progress.started("edit_blocks")
        assertEquals("Using edit_blocks…", request.activity)

        var shown: String? = null
        runBlocking {
            progress
                .showingApprovals(ApprovalGate { shown = request.activity })
                .approve(emptyList())
        }
        assertEquals("Waiting for approval…", shown)
        assertEquals("Using edit_blocks…", request.activity)

        finish("edit_blocks", "set", Access.WRITE)
        assertNull(request.activity)
    }

    @Test
    fun `changes count the actions that changed something`() {
        finish("edit_blocks", "set", Access.WRITE)
        finish("get_blocks", "block", Access.READ)
        finish("edit_blocks", "set", Access.WRITE)
        finish("edit_blocks", "fill", Access.DESTRUCTIVE, failed = true)
        finish("move_entity", "teleport", Access.WRITE)

        assertEquals(listOf("edit_blocks.set ×2", "move_entity.teleport"), progress.changes())
    }
}
