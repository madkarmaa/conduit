package top.madkarma.conduit.game

import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.server.MinecraftServer

class EditJournalTest {
    private val wolf = UUID(0, 1)
    private val steve = UUID(0, 2)

    private class Change(override val subject: UUID?, private val changed: Boolean = true) :
        UndoableChange {
        override fun changed() = changed

        override fun undo(server: MinecraftServer) {}
    }

    private fun group(id: Int, vararg changes: UndoableChange) =
        EditGroup(id, "Alex", "edit_entity.name", Instant.EPOCH, emptyList(), changes.toList())

    @AfterTest
    fun forget() {
        EditJournal.clear()
    }

    @Test
    fun `changes of the same entity or player overlap`() {
        assertTrue(group(2, Change(wolf)).overlaps(group(1, Change(steve), Change(wolf))))
        assertFalse(group(2, Change(wolf)).overlaps(group(1, Change(steve))))
    }

    @Test
    fun `changes without a subject never overlap by it`() {
        assertFalse(group(2, Change(null)).overlaps(group(1, Change(null))))
    }

    @Test
    fun `a call that changed nothing leaves no edit behind`() {
        EditJournal.record("Alex", "edit_entity.sit") { EditJournal.before { Change(wolf, false) } }
        assertEquals(emptyList(), EditJournal.groups())

        EditJournal.record("Alex", "edit_entity.name") { EditJournal.before { Change(wolf) } }
        assertEquals(listOf("edit_entity.name"), EditJournal.groups().map { it.action })
    }

    @Test
    fun `changes outside recorded calls are not saved`() {
        var saved = false
        EditJournal.before {
            saved = true
            Change(wolf)
        }

        assertFalse(saved)
    }
}
