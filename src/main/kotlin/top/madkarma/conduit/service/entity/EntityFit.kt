package top.madkarma.conduit.service.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

private const val FIT_REACH = 3

// Whole-block offsets keep the feet at the height of the target, such as on top of a floor.
private val FIT_OFFSETS: List<Vec3> =
    (-FIT_REACH..FIT_REACH)
        .flatMap { dx ->
            (-FIT_REACH..FIT_REACH).flatMap { dy ->
                (-FIT_REACH..FIT_REACH).map { dz ->
                    Vec3(dx.toDouble(), dy.toDouble(), dz.toDouble())
                }
            }
        }
        .sortedBy { it.lengthSqr() }

/** Whether the entity and its passengers fit at the position without touching blocks. */
internal fun Entity.fitsAt(level: ServerLevel, position: Vec3): Boolean {
    val offset = position.subtract(position())
    return selfAndPassengers.allMatch { level.noCollision(it, it.boundingBox.move(offset)) }
}

/**
 * The target when the entity and its passengers fit there, or else the nearest position within 3
 * blocks where they do, standing on something when the target does. Null when none does.
 */
internal fun Entity.fittingPosition(level: ServerLevel, target: Vec3): Vec3? {
    if (fitsAt(level, target)) return target
    val grounded = standsOn(level, target)
    return FIT_OFFSETS.asSequence().map(target::add).firstOrNull {
        fitsAt(level, it) && (!grounded || standsOn(level, it))
    }
}

// An unloaded chunk counts as nothing to stand on, since reading it would load it.
private fun standsOn(level: ServerLevel, position: Vec3): Boolean {
    val below = BlockPos.containing(position.x, position.y - 0.01, position.z)
    return level.isLoaded(below) &&
        !level.getBlockState(below).getCollisionShape(level, below).isEmpty
}
