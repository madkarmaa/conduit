package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals

class BatchToolTest {
    private val offered = setOf("edit_blocks", "conduit_tool")

    @Test
    fun `a batch takes the names that clients show`() {
        assertEquals("edit_blocks", serverToolName("edit_blocks", offered))
        assertEquals("edit_blocks", serverToolName("conduit_edit_blocks", offered))
        assertEquals("edit_blocks", serverToolName("mcp__conduit__edit_blocks", offered))
    }

    @Test
    fun `a name the server offers stays as it is`() {
        assertEquals("conduit_tool", serverToolName("conduit_tool", offered))
        assertEquals("unknown", serverToolName("unknown", offered))
    }
}
