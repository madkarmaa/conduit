package top.madkarma.conduit.service.server

import kotlin.test.Test
import kotlin.test.assertEquals
import top.madkarma.conduit.invalidInputMessage

class ServerSettingsTest {
    @Test
    fun `every setting has its own name`() {
        val names = LIVE_SETTINGS.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `only the settings about who may join or act need set_access`() {
        assertEquals(
            setOf(
                "whitelist",
                "enforce_whitelist",
                "operator_permission_level",
                "hide_online_players",
                "replies_to_status",
                "accepts_transfers",
            ),
            LIVE_SETTINGS.filter { it.access }.map { it.name }.toSet(),
        )
    }

    @Test
    fun `a setting names the values it takes`() {
        assertEquals("a whole number from 2 to 32", setting("view_distance").accepts)
        assertEquals("a whole number of at least 0", setting("max_players").accepts)
        assertEquals("peaceful, easy, normal, hard", setting("difficulty").accepts)
        assertEquals("true or false", setting("allow_flight").accepts)
    }

    @Test
    fun `an unknown setting is invalid`() {
        assertEquals(
            "Unknown setting: build_height",
            invalidInputMessage { setting("build_height") },
        )
    }
}
