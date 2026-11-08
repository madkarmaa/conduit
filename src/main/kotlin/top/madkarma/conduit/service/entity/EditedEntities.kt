package top.madkarma.conduit.service.entity

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import top.madkarma.conduit.game.EditJournal
import top.madkarma.conduit.game.EntitySnapshot
import top.madkarma.conduit.game.entity
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.service.player.PlayerSnapshot

/**
 * Looks up an entity that an action is about to change, and saves it so undo can put it back. Every
 * action that changes an existing entity looks it up here, so none can forget it.
 */
internal fun MinecraftServer.editedEntity(uuid: String, changesLater: Boolean = false): Entity {
    val entity = entity(uuid)
    EditJournal.before {
        // Loading a player's full data would be unsafe, so players save what actions change.
        if (entity is ServerPlayer) PlayerSnapshot.of(entity)
        else EntitySnapshot.of(entity, changesLater)
    }
    return entity
}

internal fun MinecraftServer.editedLiving(uuid: String): LivingEntity =
    editedEntity(uuid) as? LivingEntity ?: failedPrecondition("Entity $uuid is not a living entity")
