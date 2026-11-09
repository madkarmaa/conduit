package top.madkarma.conduit.bot

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationsTest {
    @Test
    fun `a cancelled request is reported once to the next request`() {
        Conversations.markCancelled("test-folder-1")

        assertTrue(Conversations.takeCancelled("test-folder-1"))
        assertFalse(Conversations.takeCancelled("test-folder-1"))
    }

    @Test
    fun `other conversations do not see the cancel`() {
        Conversations.markCancelled("test-folder-2")

        assertFalse(Conversations.takeCancelled("test-folder-3"))
        Conversations.takeCancelled("test-folder-2")
    }
}
