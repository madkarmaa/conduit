package top.madkarma.conduit.service.player

import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.NameAndId
import net.minecraft.util.ProblemReporter
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.UndoableChange
import top.madkarma.conduit.service.entity.saveEntity

// The fields of the saved data that the actions on online players change. PlayerItems has the
// items.
private val CHANGED_FIELDS =
    listOf(
        "Pos",
        "Rotation",
        "Dimension",
        "Health",
        "active_effects",
        "attributes",
        "foodLevel",
        "foodSaturationLevel",
        "foodExhaustionLevel",
        "foodTickTimer",
        "XpLevel",
        "XpP",
        "XpTotal",
        "playerGameType",
        "previousPlayerGameType",
    )

/**
 * A player before a recorded call changed them, as saved data: the fields that actions change for
 * online players, or all of it for offline ones, and whether they were an operator. Undo puts it
 * back whether the player is online or offline by then.
 */
class PlayerSnapshot
private constructor(
    private val server: MinecraftServer,
    override val subject: UUID,
    private val data: CompoundTag,
    private val wholeData: Boolean,
    private val operator: Pair<NameAndId, Boolean>?,
) : UndoableChange {
    override fun changed(): Boolean {
        val wasOperator = operator?.let { (profile, was) -> server.playerList.isOp(profile) != was }
        return dataNow() != data || wasOperator == true
    }

    override fun undo(server: MinecraftServer) {
        operator?.let { (profile, was) ->
            if (server.playerList.isOp(profile) == was) return@let
            if (was) server.playerList.op(profile) else server.playerList.deop(profile)
        }

        val player = server.playerList.getPlayer(subject)
        if (player != null) return player.restore(data)

        server.requireOffline(subject)
        val saved =
            if (wholeData) data.copy() else server.readPlayerData(subject).merge(data.copy())
        server.writePlayerData(subject, saved)
    }

    private fun dataNow(): CompoundTag {
        val player = server.playerList.getPlayer(subject)
        val now = player?.let { server.saveEntity(it) } ?: server.readPlayerData(subject)
        return if (wholeData) now else now.changedFields()
    }

    companion object {
        fun of(player: ServerPlayer): PlayerSnapshot {
            val server = player.level().server
            val profile = player.nameAndId()
            return PlayerSnapshot(
                server,
                player.uuid,
                server.saveEntity(player).changedFields(),
                wholeData = false,
                operator = profile to server.playerList.isOp(profile),
            )
        }

        /** The saved data of an offline player, before an edit of the file. */
        fun saved(server: MinecraftServer, id: UUID, data: CompoundTag) =
            PlayerSnapshot(server, id, data.copy(), wholeData = true, operator = null)
    }
}

private fun CompoundTag.changedFields(): CompoundTag {
    val fields = CompoundTag()
    CHANGED_FIELDS.forEach { name -> get(name)?.let { fields.put(name, it.copy()) } }
    return fields
}

/** Applies the changed fields of saved data to the online player. */
private fun ServerPlayer.restore(data: CompoundTag) {
    val input = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), data)

    setGameMode(GameType.byId(input.getIntOr("playerGameType", gameMode().id)))
    foodData.readAdditionalSaveData(input)
    experienceProgress = input.getFloatOr("XpP", experienceProgress)
    totalExperience = input.getIntOr("XpTotal", totalExperience)
    // Also sends the experience to the client.
    setExperienceLevels(input.getIntOr("XpLevel", experienceLevel))

    input.read("attributes", AttributeInstance.Packed.LIST_CODEC).ifPresent { saved ->
        saved.forEach { attributes.getInstance(it.attribute())?.baseValue = it.baseValue() }
    }
    removeAllEffects()
    input.read("active_effects", MobEffectInstance.CODEC.listOf()).ifPresent { effects ->
        effects.forEach { addEffect(it) }
    }
    // After the attributes, since the max health limits the health.
    health = input.getFloatOr("Health", health)

    moveBack(input)
}

private fun ServerPlayer.moveBack(input: ValueInput) {
    val position = input.read("Pos", Vec3.CODEC).orElse(position())
    val rotation = input.read("Rotation", Vec2.CODEC).orElse(Vec2(yRot, xRot))
    val dimension = input.read("Dimension", Level.RESOURCE_KEY_CODEC).orElse(level().dimension())
    val level = level().server.getLevel(dimension) ?: return

    val moved = level != level() || position != position()
    if (!moved && rotation.x == yRot && rotation.y == xRot) return
    teleportTo(level, position.x, position.y, position.z, emptySet(), rotation.x, rotation.y, true)
}
