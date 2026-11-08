package top.madkarma.conduit.game

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

// Restoring skips drops, neighbor updates and placement effects, like a structure placement.
private const val RESTORE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SKIP_ALL_SIDEEFFECTS

/**
 * The blocks of one dimension as they were before an edit. It packs positions into longs, and keeps
 * each block entity's data under the index of its position.
 */
class BlockSnapshot(val dimension: ResourceKey<Level>) {
    private val positions = LongArrayList()
    private val states = ArrayList<BlockState>()
    private val blockEntities = Int2ObjectOpenHashMap<CompoundTag>()
    private var saved: LongOpenHashSet? = LongOpenHashSet()

    val size: Int
        get() = positions.size

    /** Saves a block unless this snapshot already has it. Returns whether it saved it. */
    fun save(level: ServerLevel, pos: BlockPos): Boolean {
        val packed = pos.asLong()
        if (saved?.add(packed) != true) return false

        positions.add(packed)
        states.add(level.getBlockState(pos))
        level.getBlockEntity(pos)?.let {
            blockEntities.put(positions.size - 1, it.saveWithoutMetadata(level.registryAccess()))
        }
        return true
    }

    /** Drops the blocks that the edit left as they were. Saving ends here. */
    fun keepChanged(level: ServerLevel): BlockSnapshot {
        val changed = BlockSnapshot(dimension).also { it.saved = null }
        val cursor = BlockPos.MutableBlockPos()

        for (index in 0 until size) {
            cursor.set(positions.getLong(index))
            if (isUnchanged(level, cursor, index)) continue

            changed.positions.add(positions.getLong(index))
            changed.states.add(states[index])
            blockEntities[index]?.let { changed.blockEntities.put(changed.size - 1, it) }
        }

        return changed
    }

    fun overlaps(other: BlockSnapshot): Boolean {
        if (dimension != other.dimension) return false

        val (smaller, larger) = if (size <= other.size) this to other else other to this
        val lookup = LongOpenHashSet(smaller.positions)
        return (0 until larger.size).any { lookup.contains(larger.positions.getLong(it)) }
    }

    fun restore(server: MinecraftServer) {
        val level = server.getLevel(dimension) ?: return
        val cursor = BlockPos.MutableBlockPos()

        for (index in 0 until size) {
            cursor.set(positions.getLong(index))
            level.setBlock(cursor, states[index], RESTORE_FLAGS)

            val nbt = blockEntities[index] ?: continue
            level.getBlockEntity(cursor)?.let { level.loadBlockEntity(it, nbt.copy()) }
        }
    }

    private fun isUnchanged(level: ServerLevel, pos: BlockPos, index: Int): Boolean {
        if (level.getBlockState(pos) != states[index]) return false

        val before = blockEntities[index]
        val after = level.getBlockEntity(pos)?.saveWithoutMetadata(level.registryAccess())
        return before == after
    }
}
