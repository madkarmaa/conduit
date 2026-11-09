package top.madkarma.conduit.game

import java.util.UUID
import net.minecraft.server.MinecraftServer

/** Something besides blocks that a recorded call changed, which undo can put back. */
interface UndoableChange {
    /** The entity or player that the change is about. Changes of the same one overlap. */
    val subject: UUID?
        get() = null

    /** Blocks that the change holds, which count toward the undo limits. */
    val blocks: Long
        get() = 0

    /** Called once the call ended. False when the call left it as it was, so undo forgets it. */
    fun changed(): Boolean = true

    /** Whether undoing this would also revert part of the other change. */
    fun overlaps(other: UndoableChange): Boolean = subject != null && subject == other.subject

    /** Throws when undo cannot put the change back now, before undo changes anything. */
    fun checkUndo(server: MinecraftServer) {}

    fun undo(server: MinecraftServer)
}
