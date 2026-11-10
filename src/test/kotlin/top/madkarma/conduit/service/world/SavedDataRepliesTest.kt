package top.madkarma.conduit.service.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedDataRepliesTest {
    @Test
    fun `short saved data stays whole`() {
        assertEquals("{Names:[\"Home\"]}", capSavedData("{Names:[\"Home\"]}"))
    }

    @Test
    fun `long saved data ends with the top-level keys to read by path`() {
        val names = (1..3000).joinToString(",") { "\"Place $it\"" }
        val snbt = "{Places:[$names],Groups:[]}"

        val capped = capSavedData(snbt)

        assertTrue(capped.startsWith(snbt.take(MAX_SAVED_DATA_CHARS)))
        assertTrue("${snbt.length - MAX_SAVED_DATA_CHARS} more characters" in capped)
        assertTrue(capped.endsWith("The top-level keys are Groups, Places."))
    }
}
