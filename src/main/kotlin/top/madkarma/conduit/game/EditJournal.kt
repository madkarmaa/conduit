package top.madkarma.conduit.game

import java.time.Instant
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.BoundingBox

private const val DEFAULT_MAX_BLOCKS = 2_000_000L

/**
 * Records the blocks that tool calls change, so undo can put them back. Only the server thread uses
 * it, and it forgets everything when the server stops.
 */
object EditJournal {
    /** The most blocks that all groups hold together. Zero turns recording off. */
    @Volatile var maxBlocks: () -> Long = { DEFAULT_MAX_BLOCKS }

    private val groups = ArrayDeque<EditGroup>()
    private val current = ThreadLocal<Recorder>()
    private var nextId = 1

    fun <T> record(caller: String, action: String, edit: () -> T): Recorded<T> {
        val limit = maxBlocks()
        // A call inside a recorded call belongs to the outer call's group.
        if (limit <= 0 || current.get() != null) return Recorded(edit(), undoable = true)

        val recorder = Recorder(limit)
        current.set(recorder)
        try {
            val result = edit()
            return Recorded(result, !recorder.tooLarge)
        } finally {
            // A call that fails halfway still changed blocks, so its group is kept too.
            current.remove()
            keep(recorder, caller, action)
        }
    }

    /** Saves a block before a recorded call changes it. Does nothing outside recorded calls. */
    fun before(level: ServerLevel, pos: BlockPos) {
        current.get()?.save(level, pos)
    }

    /** Saves every block of a box before a recorded call changes some of them. */
    fun beforeBox(level: ServerLevel, box: BoundingBox) {
        current.get()?.saveBox(level, box)
    }

    /** The recorded groups, newest first. */
    fun groups(): List<EditGroup> = groups.reversed()

    /** Restores the given groups, or else the caller's newest one, and forgets them. */
    fun undo(server: MinecraftServer, ids: List<Int>, caller: String): List<EditGroup> {
        val chosen = choose(ids, caller)
        checkNoNewerOverlap(chosen)

        for (group in chosen.sortedByDescending { it.id }) {
            group.snapshots.forEach { it.restore(server) }
            groups.remove(group)
        }
        return chosen
    }

    fun clear() {
        groups.clear()
    }

    private fun keep(recorder: Recorder, caller: String, action: String) {
        if (recorder.tooLarge) return

        val snapshots = recorder.changed()
        if (snapshots.isEmpty()) return

        groups.addLast(EditGroup(nextId++, caller, action, Instant.now(), snapshots))
        val limit = maxBlocks()
        while (groups.sumOf { it.blocks } > limit) groups.removeFirst()
    }

    private fun choose(ids: List<Int>, caller: String): List<EditGroup> {
        if (ids.isEmpty()) {
            val newest = groups.lastOrNull { it.caller == caller }
            return listOf(newest ?: notFound("$caller has no recorded edits to undo"))
        }

        return ids.distinct().map { id ->
            groups.firstOrNull { it.id == id } ?: notFound("No recorded edit #$id")
        }
    }

    // Restoring an older group over a newer one would also revert part of the newer one.
    private fun checkNoNewerOverlap(chosen: List<EditGroup>) {
        for (group in chosen) {
            val newer =
                groups.firstOrNull { it.id > group.id && it !in chosen && it.overlaps(group) }
                    ?: continue
            failedPrecondition(
                "Edit #${newer.id} (${newer.action}) changed the same blocks after #${group.id}. " +
                    "Undo it first, or both together.",
            )
        }
    }
}
