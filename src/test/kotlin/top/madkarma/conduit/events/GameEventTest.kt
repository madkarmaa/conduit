package top.madkarma.conduit.events

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.madkarma.conduit.parseJson

class GameEventTest {
    private val alexUuid = "00000000-0000-0000-0000-000000000001"

    private fun event(payload: String) = GameEvent(EventType.CHAT, 0, parseJson(payload))

    @Test
    fun `involves the player by name in any case`() {
        val chat = event("""{"player": "Alex", "message": "hi"}""")

        assertTrue(chat.involves(listOf("alex")))
        assertFalse(chat.involves(listOf("Steve")))
    }

    @Test
    fun `involves the player by UUID`() {
        assertTrue(event("""{"playerUuid": "$alexUuid"}""").involves(listOf(alexUuid.uppercase())))
    }

    @Test
    fun `involves the entity by name or UUID`() {
        val death =
            event(
                """{"entity": {"name": "Alex", "uuid": "$alexUuid", "type": "minecraft:player"}}""",
            )

        assertTrue(death.involves(listOf("Steve", "Alex")))
        assertTrue(death.involves(listOf(alexUuid)))
        assertFalse(death.involves(listOf("minecraft:player")))
    }

    @Test
    fun `ignores fields that do not name a player`() {
        val attack = event("""{"player": "Steve", "target": {"name": "Alex"}, "message": "Alex"}""")

        assertFalse(attack.involves(listOf("Alex")))
        assertFalse(event("{}").involves(listOf("Alex")))
    }
}
