package top.madkarma.conduit.service.entity

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityReference
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.feline.Cat
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.animal.wolf.Wolf
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.mixin.AbstractHorseAccessor
import top.madkarma.conduit.service.player.lastKnownName
import top.madkarma.conduit.service.player.resolvePlayerId

/** Ends the anger of a neutral mob, such as a wolf, a bee or a piglin. */
internal fun MinecraftServer.calm(uuid: String) {
    val entity = editedEntity(uuid)
    val mob = entity as? NeutralMob ?: failedPrecondition("A ${entity.typeId()} is never angry")
    mob.stopBeingAngry()
}

/**
 * Tames the animal for the player, such as a wolf, a cat or a horse. The player may be offline.
 * Returns the owner's name.
 */
internal fun MinecraftServer.changeOwner(uuid: String, player: String): String {
    val animal = editedEntity(uuid)
    if (animal !is TamableAnimal && animal !is AbstractHorse) {
        failedPrecondition("A ${animal.typeId()} cannot be tamed")
    }

    val owner = resolvePlayerId(player)
    val reference = EntityReference.of<LivingEntity>(owner)

    if (animal is TamableAnimal) {
        // Taming an animal for the first time sets it up, such as a wolf's higher health.
        if (!animal.isTame) animal.setTame(true, true)
        animal.setOwnerReference(reference)
    } else if (animal is AbstractHorse) {
        animal.setTamed(true)
        (animal as AbstractHorseAccessor).setOwnerReference(reference)
    }

    return lastKnownName(owner).ifEmpty { player }
}

/** Only wolves, cats and parrots sit, and only once they are tamed. */
internal fun MinecraftServer.changeSitting(uuid: String, sitting: Boolean) {
    val entity = editedEntity(uuid)
    val animal =
        entity.takeIf { it is Wolf || it is Cat || it is Parrot } as? TamableAnimal
            ?: failedPrecondition("A ${entity.typeId()} cannot sit. Wolves, cats and parrots can.")
    if (!animal.isTame) failedPrecondition("Only tamed animals sit. Tame it with owner first.")

    animal.setOrderedToSit(sitting)
    animal.setInSittingPose(sitting)
}

private fun Entity.typeId(): String = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()
