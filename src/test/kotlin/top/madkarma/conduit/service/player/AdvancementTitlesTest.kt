package top.madkarma.conduit.service.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancementTitlesTest {
    @Test
    fun `a title matches a filter written like an id`() {
        assertTrue(looselyContains("How Did We Get Here?", "how_did_we_get_here"))
        assertTrue(looselyContains("How Did We Get Here?", "how did"))
    }

    @Test
    fun `an empty filter matches every title`() {
        assertTrue(looselyContains("Stone Age", ""))
    }

    @Test
    fun `a title does not match other words`() {
        assertFalse(looselyContains("Stone Age", "iron"))
    }
}
