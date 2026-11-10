package top.madkarma.conduit.service.player

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.core.UUIDUtil

class PreferredIdTest {
    private val offline = UUIDUtil.createOfflinePlayerUUID("Alex")
    private val online = UUID.fromString("cf7962e2-0efc-49f0-80ef-5768582916d7")

    @Test
    fun `an offline server picks the offline UUID of the name`() {
        assertEquals(offline, preferredId("Alex", listOf(online, offline), joinsOffline = true))
        assertEquals(offline, preferredId("Alex", listOf(offline, online), joinsOffline = true))
    }

    @Test
    fun `an online server picks the other UUID`() {
        assertEquals(online, preferredId("Alex", listOf(offline, online), joinsOffline = false))
    }

    @Test
    fun `a single saved file is used whatever its UUID`() {
        assertEquals(online, preferredId("Alex", listOf(online), joinsOffline = true))
        assertEquals(offline, preferredId("Alex", listOf(offline), joinsOffline = false))
    }
}
