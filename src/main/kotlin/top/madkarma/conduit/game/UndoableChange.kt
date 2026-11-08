package top.madkarma.conduit.game

import net.minecraft.server.MinecraftServer

/** Something besides blocks that a recorded call changed, which undo can put back. */
interface UndoableChange {
    /** Called once the call ended. False when the call left it as it was, so undo forgets it. */
    fun changed(): Boolean = true

    /** Whether undoing this would also revert part of the other change. */
    fun overlaps(other: UndoableChange): Boolean = false

    fun undo(server: MinecraftServer)
}
