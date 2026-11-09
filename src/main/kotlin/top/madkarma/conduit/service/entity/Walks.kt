package top.madkarma.conduit.service.entity

import java.util.UUID
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3

private const val REPATH_INTERVAL_TICKS = 10
private const val MAX_WALK_TICKS = 2 * 60 * 20
private const val ARRIVAL_DISTANCE = 2.0

/**
 * The walks that move_entity started. A walk to an entity paths again every few ticks, so the mob
 * keeps up with a moving target, and a walk to a position paths again when the mob lost its path.
 * All access happens on the server thread.
 */
object Walks {
    private class Walk(
        val mob: Mob,
        val target: Entity?,
        val position: Vec3,
        val speed: Double,
        val endsAtTick: Int,
    ) {
        fun goal(): Vec3 = target?.position() ?: position
    }

    private val walks = mutableMapOf<UUID, Walk>()

    fun start(server: MinecraftServer, mob: Mob, target: Entity?, position: Vec3, speed: Double) {
        walks[mob.uuid] = Walk(mob, target, position, speed, server.tickCount + MAX_WALK_TICKS)
    }

    fun end(mob: Mob) {
        walks.remove(mob.uuid)
    }

    fun clear() {
        walks.clear()
    }

    /** Mobs on a walk must not despawn when they get far from players on the way. */
    @JvmStatic fun isWalking(mob: Mob): Boolean = mob.uuid in walks

    fun onTick(server: MinecraftServer) {
        if (walks.isEmpty() || server.tickCount % REPATH_INTERVAL_TICKS != 0) return

        walks.values.removeIf { walk -> hasEnded(walk, server.tickCount) }
        for (walk in walks.values) {
            val navigation = walk.mob.navigation
            when {
                walk.target != null -> navigation.moveTo(walk.target, walk.speed)
                navigation.isDone ->
                    navigation.moveTo(walk.position.x, walk.position.y, walk.position.z, walk.speed)
            }
        }
    }

    private fun hasEnded(walk: Walk, tick: Int): Boolean {
        val mob = walk.mob
        val target = walk.target
        val targetGone = target != null && (target.isRemoved || target.level() !== mob.level())
        val arrived = mob.position().distanceTo(walk.goal()) <= ARRIVAL_DISTANCE
        return mob.isRemoved || targetGone || arrived || tick >= walk.endsAtTick
    }
}
