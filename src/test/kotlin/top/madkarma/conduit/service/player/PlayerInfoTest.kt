package top.madkarma.conduit.service.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlinx.serialization.descriptors.elementNames

class PlayerInfoTest {
    // The agent's provider and the telemetry file see every reply, and an IP address identifies a
    // player outside the game.
    @Test
    fun `player info has no IP address`() {
        val names = PlayerInfo.serializer().descriptor.elementNames.toList()

        assertFalse("ipAddress" in names, "Fields: $names")
    }
}
