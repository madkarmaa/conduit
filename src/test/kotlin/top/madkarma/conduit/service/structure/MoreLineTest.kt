package top.madkarma.conduit.service.structure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoreLineTest {
    private fun groups(vararg counts: Int) = counts.mapIndexed { index, count ->
        BlockDifference("block_$index", count, emptyList())
    }

    @Test
    fun `no line while every group is listed`() {
        assertNull(moreLine(groups(*IntArray(20) { 1 })))
    }

    @Test
    fun `the line counts the blocks past the listed groups, not the groups`() {
        assertEquals(
            "5 more blocks of 2 other kinds",
            moreLine(groups(*IntArray(20) { 9 }, 3, 2)),
        )
    }
}
