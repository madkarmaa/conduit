package top.madkarma.conduit.service.entity

import java.util.UUID
import kotlin.math.roundToInt
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import top.madkarma.conduit.game.*

/** Where a walk goes: a fixed position, or an entity that may move. */
class WalkGoal(val position: Vector3?, val target: String?)

/** A walk that started: the target's name when it follows one, the goal and how far it is. */
class StartedWalk(val targetName: String?, val goal: Vector3, val distance: Double)

/**
 * Starts a mob walking along a path to a position or an entity, at speed times its own walking
 * speed. A walk to an entity keeps following it until the mob arrives.
 */
internal fun MinecraftServer.walkTo(uuid: String, goal: WalkGoal, speed: Double): StartedWalk {
    val mob = mob(uuid, changesLater = true)
    if (mob.isNoAi) failedPrecondition("The mob has no AI, so it cannot walk")

    val target = goal.target?.let { walkTarget(it) }
    if (target != null && target.level() !== mob.level()) {
        failedPrecondition("The target is in another dimension than the mob")
    }

    val position =
        target?.position() ?: (goal.position ?: invalidInput("Set position or target")).toMc()
    // A new walk replaces the old one, also when it finds no path.
    Walks.end(mob)
    val found =
        if (target != null) mob.navigation.moveTo(target, speed)
        else mob.navigation.moveTo(position.x, position.y, position.z, speed)
    // Mobs only path from the ground, so a falling or just teleported mob paths on a later tick.
    if (!found && mob.onGround()) failedPrecondition(noPathMessage(mob, position.toVector()))

    Walks.start(this, mob, target, position, speed)
    return StartedWalk(
        target?.name?.string,
        position.toVector(),
        mob.position().distanceTo(position),
    )
}

internal fun MinecraftServer.stopWalking(uuid: String) {
    val mob = mob(uuid)
    mob.navigation.stop()
    Walks.end(mob)
}

private fun MinecraftServer.walkTarget(ref: String): Entity {
    val isUuid = runCatching { UUID.fromString(ref) }.isSuccess
    return if (isUuid) entity(ref) else player(ref)
}

// Mobs only path as far as their follow range, so a far goal needs waypoints.
private fun noPathMessage(mob: Mob, goal: Vector3): String {
    val range = mob.getAttributeValue(Attributes.FOLLOW_RANGE).roundToInt()
    val distance = mob.position().distanceTo(goal.toMc()).roundToInt()
    val coordinates = "${goal.x.roundToInt()} ${goal.y.roundToInt()} ${goal.z.roundToInt()}"
    return "The mob found no path to $coordinates, $distance blocks away. It paths up to " +
        "about $range blocks, so walk it to a nearer waypoint or teleport it closer."
}

// A walk only moves the mob on later ticks, so undo keeps it even though nothing changed yet.
private fun MinecraftServer.mob(uuid: String, changesLater: Boolean = false): Mob {
    val entity = editedEntity(uuid, changesLater)
    val type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
    return entity as? Mob ?: failedPrecondition("Only mobs can walk, and $type is not one")
}
