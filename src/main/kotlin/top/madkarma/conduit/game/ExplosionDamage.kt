package top.madkarma.conduit.game

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

// Restoring skips drops, neighbor updates and placement effects, like a structure placement.
private const val RESTORE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SKIP_ALL_SIDEEFFECTS

/**
 * The blocks one explosion destroyed, as they were before and after it, and the items it dropped.
 * Undo puts back only the blocks that are still as the explosion left them, so blocks that players
 * placed since stay, and removes the dropped items that nobody picked up.
 */
class ExplosionDamage(val dimension: ResourceKey<Level>, val center: BlockPos) : UndoableChange {
    private class Destroyed(val pos: Long, val before: BlockState, val data: CompoundTag?) {
        var after: BlockState = before
    }

    private var destroyed = mutableListOf<Destroyed>()
    private val items = mutableListOf<UUID>()

    override val blocks: Long
        get() = destroyed.size.toLong()

    fun saveBefore(level: ServerLevel, pos: BlockPos) {
        val data = level.getBlockEntity(pos)?.saveWithoutMetadata(level.registryAccess())
        destroyed += Destroyed(pos.asLong(), level.getBlockState(pos), data)
    }

    fun dropped(item: ItemEntity) {
        items += item.uuid
    }

    /** Saves the blocks as the explosion left them, and forgets the ones it did not change. */
    fun saveAfter(level: ServerLevel) {
        val cursor = BlockPos.MutableBlockPos()
        destroyed.forEach { it.after = level.getBlockState(cursor.set(it.pos)) }
        destroyed = destroyed.filterTo(mutableListOf()) { it.after != it.before }
    }

    fun isNear(dimension: ResourceKey<Level>, pos: BlockPos, radius: Int): Boolean =
        dimension == this.dimension && center.closerThan(pos, radius.toDouble())

    override fun overlaps(other: UndoableChange): Boolean {
        if (other !is ExplosionDamage || other.dimension != dimension) return false

        val mine = LongOpenHashSet(destroyed.map { it.pos })
        return other.destroyed.any { mine.contains(it.pos) }
    }

    override fun undo(server: MinecraftServer) {
        val level = server.getLevel(dimension) ?: return
        val cursor = BlockPos.MutableBlockPos()

        for (block in destroyed) {
            val current = level.getBlockState(cursor.set(block.pos))
            // Explosions light fires, which burn out to the air the explosion left.
            if (current != block.after && current.block !is BaseFireBlock) continue

            level.setBlock(cursor, block.before, RESTORE_FLAGS)
            val data = block.data ?: continue
            level.getBlockEntity(cursor)?.let { level.loadBlockEntity(it, data.copy()) }
        }

        items.forEach { level.getEntity(it)?.discard() }
    }
}
