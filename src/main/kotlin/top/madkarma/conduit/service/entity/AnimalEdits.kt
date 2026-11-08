package top.madkarma.conduit.service.entity

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityReference
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.TamableAnimal
import top.madkarma.conduit.game.entity
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.service.player.lastKnownName
import top.madkarma.conduit.service.player.resolvePlayerId

/** Ends the anger of a neutral mob, such as a wolf, a bee or a piglin. */
internal fun MinecraftServer.calm(uuid: String) {
    val mob = entity(uuid) as? NeutralMob ?: failedPrecondition("Entity $uuid is never angry")
    mob.stopBeingAngry()
}

/** Tames the animal for the player, who may be offline. Returns the owner's name. */
internal fun MinecraftServer.changeOwner(uuid: String, player: String): String {
    val animal = tamable(uuid)
    val owner = resolvePlayerId(player)

    // Taming an animal for the first time sets it up, such as a wolf's higher health.
    if (!animal.isTame) animal.setTame(true, true)
    animal.setOwnerReference(EntityReference.of(owner))
    return lastKnownName(owner).ifEmpty { player }
}

internal fun MinecraftServer.changeSitting(uuid: String, sitting: Boolean) {
    val animal = tamable(uuid)
    animal.setOrderedToSit(sitting)
    animal.setInSittingPose(sitting)
}

private fun MinecraftServer.tamable(uuid: String): TamableAnimal =
    entity(uuid) as? TamableAnimal ?: failedPrecondition("Entity $uuid cannot be tamed")
