package top.madkarma.conduit.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.madkarma.conduit.invalidInputMessage

class NameMatchTest {
    private val names = listOf("alex1pg", "Toto_2506", "MacintoshExe", "AngryAntonio", "AntonioC")

    @Test
    fun `a unique prefix matches in any case`() {
        assertEquals("Toto_2506", uniqueNameMatch(names, "toto"))
        assertEquals("MacintoshExe", uniqueNameMatch(names, "MACINTOSH"))
    }

    @Test
    fun `no match is null`() {
        assertNull(uniqueNameMatch(names, "steve"))
    }

    @Test
    fun `several matches fail and name them`() {
        assertEquals(
            "\"an\" matches several players: AngryAntonio, AntonioC",
            invalidInputMessage { uniqueNameMatch(names, "an") },
        )
    }
}
