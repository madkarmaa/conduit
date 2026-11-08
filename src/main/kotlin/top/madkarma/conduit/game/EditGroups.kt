package top.madkarma.conduit.game

import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.BoundingBox

/** One recorded tool call, with the blocks it changed as they were before. */
class EditGroup(
    val id: Int,
    val caller: String,
    val action: String,
    val time: Instant,
    val snapshots: List<BlockSnapshot>,
) {
    val blocks: Long = snapshots.sumOf { it.size.toLong() }

    fun overlaps(other: EditGroup): Boolean = snapshots.any { mine ->
        other.snapshots.any { mine.overlaps(it) }
    }
}

/** What a recorded call returned, and whether undo can revert it. */
class Recorded<T>(val result: T, val undoable: Boolean)

/** The blocks that one call saves, per dimension. */
internal class Recorder(private val limit: Long) {
    private val snapshots = LinkedHashMap<ServerLevel, BlockSnapshot>()
    private var count = 0L

    var tooLarge = false
        private set

    fun save(level: ServerLevel, pos: BlockPos) {
        if (tooLarge) return

        val snapshot = snapshots.getOrPut(level) { BlockSnapshot(level.dimension()) }
        if (snapshot.save(level, pos)) count++
        if (count > limit) giveUp()
    }

    fun saveBox(level: ServerLevel, box: BoundingBox) {
        val minY = max(box.minY(), level.minY)
        val maxY = min(box.maxY(), level.maxY)
        if (tooLarge || minY > maxY) return

        val volume =
            (box.maxX() - box.minX() + 1L) * (maxY - minY + 1L) * (box.maxZ() - box.minZ() + 1L)
        if (count + volume > limit) return giveUp()

        for (pos in
            BlockPos.betweenClosed(box.minX(), minY, box.minZ(), box.maxX(), maxY, box.maxZ())) {
            save(level, pos)
        }
    }

    fun changed(): List<BlockSnapshot> =
        snapshots.map { (level, snapshot) -> snapshot.keepChanged(level) }.filter { it.size > 0 }

    // Too many blocks to keep, so this frees what it saved.
    private fun giveUp() {
        tooLarge = true
        snapshots.clear()
    }
}
