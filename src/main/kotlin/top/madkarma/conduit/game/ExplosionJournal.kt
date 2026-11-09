package top.madkarma.conduit.game

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.ServerExplosion

private const val DEFAULT_MAX_BLOCKS = 200_000L

/**
 * Records the blocks that explosions destroy, such as creeper holes, in the edit journal, so undo
 * can repair them. Only the server thread uses it.
 */
object ExplosionJournal {
    /** The most blocks that recorded explosions hold together. Zero turns recording off. */
    @Volatile var maxBlocks: () -> Long = { DEFAULT_MAX_BLOCKS }

    private var current: ExplosionDamage? = null

    /** Called before the explosion breaks the blocks. */
    @JvmStatic
    fun before(explosion: ServerExplosion, positions: List<BlockPos>) {
        // A recorded call that made the explosion records the blocks itself.
        if (current != null || positions.isEmpty() || EditJournal.isRecording()) return
        if (maxBlocks() <= 0 || !breaksBlocks(explosion.blockInteraction)) return

        val level = explosion.level()
        val damage = ExplosionDamage(level.dimension(), BlockPos.containing(explosion.center()))
        positions.forEach { damage.saveBefore(level, it) }
        current = damage
    }

    /** Called for every entity that enters the world, to note the items the explosion drops. */
    @JvmStatic
    fun spawned(entity: Entity) {
        if (entity is ItemEntity) current?.dropped(entity)
    }

    /** Called after the explosion broke the blocks. */
    @JvmStatic
    fun after(explosion: ServerExplosion) {
        val damage = current ?: return
        current = null

        damage.saveAfter(explosion.level())
        if (damage.blocks > 0) EditJournal.keepExplosion(sourceOf(explosion), damage, maxBlocks())
    }

    private fun breaksBlocks(interaction: Explosion.BlockInteraction): Boolean =
        interaction != Explosion.BlockInteraction.KEEP &&
            interaction != Explosion.BlockInteraction.TRIGGER_BLOCK

    // Such as "Creeper", or "Primed TNT from Steve" for TNT that a player lit.
    private fun sourceOf(explosion: ServerExplosion): String {
        val direct = explosion.directSourceEntity?.name?.string ?: return "an explosion"
        val indirect = explosion.indirectSourceEntity
        if (indirect == null || indirect === explosion.directSourceEntity) return direct
        return "$direct from ${indirect.name.string}"
    }
}
