package top.madkarma.conduit.game

import kotlin.test.Test
import kotlin.test.assertEquals

class TagHintsTest {
    private val known =
        listOf(
            "c:chests",
            "c:chests/wooden",
            "c:barrels",
            "minecraft:logs",
            "minecraft:logs_that_burn",
            "minecraft:oak_logs",
        )

    @Test
    fun `a tag of another namespace or plural matches`() {
        assertEquals(listOf("c:chests", "c:chests/wooden"), similarTags("minecraft:chests", known))
        assertEquals(listOf("c:chests", "c:chests/wooden"), similarTags("minecraft:chest", known))
    }

    @Test
    fun `the closest lengths come first and at most five are named`() {
        assertEquals(
            listOf("minecraft:logs", "minecraft:oak_logs", "minecraft:logs_that_burn"),
            similarTags("log", known),
        )
    }

    @Test
    fun `an unrelated tag has no similar tags`() {
        assertEquals(emptyList(), similarTags("minecraft:beds", known))
    }
}
