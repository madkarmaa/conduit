package top.madkarma.conduit.service.entity

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Mob
import top.madkarma.conduit.game.*

/** Starts a mob walking along a path to a position, at speed times its own walking speed. */
internal fun MinecraftServer.walkTo(uuid: String, position: Vector3, speed: Double) {
    val mob = mob(uuid)
    if (mob.isNoAi) failedPrecondition("The mob has no AI, so it cannot walk")

    if (!mob.navigation.moveTo(position.x, position.y, position.z, speed)) {
        failedPrecondition("The mob found no path to ${position.x} ${position.y} ${position.z}")
    }
}

internal fun MinecraftServer.stopWalking(uuid: String) {
    mob(uuid).navigation.stop()
}

private fun MinecraftServer.mob(uuid: String): Mob {
    val entity = entity(uuid)
    val type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
    return entity as? Mob ?: failedPrecondition("Only mobs can walk, and $type is not one")
}
