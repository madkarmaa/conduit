package top.madkarma.conduit.service.events

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryTest {
    private fun line(player: String, message: String) =
        ChatLine("2026-11-10T09:00:00Z", player, "uuid-$player", message)

    @AfterTest
    fun reset() {
        ChatHistory.size = { 200 }
        ChatHistory.clear()
    }

    @Test
    fun `keeps the configured number of messages, dropping the oldest`() {
        ChatHistory.size = { 2 }
        listOf("one", "two", "three").forEach { ChatHistory.add(line("Alex", it)) }

        assertEquals(listOf("two", "three"), ChatHistory.recent(emptyList(), 50).map { it.message })
    }

    @Test
    fun `a smaller size trims the history, and zero keeps nothing`() {
        listOf("one", "two", "three").forEach { ChatHistory.add(line("Alex", it)) }

        ChatHistory.size = { 1 }
        assertEquals(listOf("three"), ChatHistory.recent(emptyList(), 50).map { it.message })

        ChatHistory.size = { 0 }
        ChatHistory.add(line("Alex", "four"))
        assertEquals(emptyList(), ChatHistory.recent(emptyList(), 50))
    }

    @Test
    fun `filters by player name or UUID and keeps the latest within the limit`() {
        ChatHistory.add(line("Alex", "hi"))
        ChatHistory.add(line("Steve", "hello"))
        ChatHistory.add(line("Alex", "bye"))

        assertEquals(listOf("hi", "bye"), ChatHistory.recent(listOf("alex"), 50).map { it.message })
        assertEquals(
            listOf("hello"),
            ChatHistory.recent(listOf("uuid-Steve"), 50).map { it.message },
        )
        assertEquals(listOf("bye"), ChatHistory.recent(emptyList(), 1).map { it.message })
    }
}
