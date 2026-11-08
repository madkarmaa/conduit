package top.madkarma.conduit.service.player

import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import top.madkarma.conduit.game.*

@Serializable
data class PlayerInfo(
    val name: String,
    val entity: EntityInfo,
    val gameMode: String,
    val foodLevel: Int,
    val saturation: Float,
    val experienceLevel: Int,
    val experienceProgress: Float,
    val totalExperience: Int,
    val selectedSlot: Int,
    val pingMs: Int,
    val operator: Boolean,
    val ipAddress: String,
    val mainHand: ItemInfo,
    val offHand: ItemInfo,
    /** What the player looks at. Only get_player info reads it. */
    val lookingAt: LookTarget? = null,
)

@Serializable data class PlayerList(val players: List<PlayerInfo>)

internal fun MinecraftServer.listPlayers(): PlayerList =
    PlayerList(playerList.players.map { describePlayer(it) })

internal fun MinecraftServer.describePlayer(ref: String): PlayerInfo {
    val player = player(ref)
    return describePlayer(player).copy(lookingAt = lookTarget(player))
}

internal fun MinecraftServer.describePlayer(player: ServerPlayer): PlayerInfo {
    val registries = registryAccess()

    return PlayerInfo(
        name = player.gameProfile.name,
        entity = player.toInfo(),
        gameMode = player.gameMode().serializedName,
        foodLevel = player.foodData.foodLevel,
        saturation = player.foodData.saturationLevel,
        experienceLevel = player.experienceLevel,
        experienceProgress = player.experienceProgress,
        totalExperience = player.totalExperience,
        selectedSlot = player.inventory.selectedSlot,
        pingMs = player.connection.latency(),
        operator = playerList.isOp(player.nameAndId()),
        ipAddress = player.ipAddress,
        mainHand = player.mainHandItem.toInfo(registries),
        offHand = player.offhandItem.toInfo(registries),
    )
}

/** Game mode is survival, creative, adventure or spectator. */
internal fun MinecraftServer.changeGameMode(ref: String, gameMode: String) {
    val mode =
        GameType.byName(gameMode.lowercase(), null) ?: invalidInput("Unknown game mode: $gameMode")
    player(ref).setGameMode(mode)
}

internal fun MinecraftServer.changeFood(ref: String, foodLevel: Int?, saturation: Float?) {
    val food = player(ref).foodData
    foodLevel?.let { food.foodLevel = it }
    saturation?.let { food.setSaturation(it) }
}

/** Points are the experience within the current level. */
internal fun MinecraftServer.changeExperience(ref: String, levels: Int?, points: Int?) {
    val player = player(ref)
    levels?.let { player.setExperienceLevels(it) }
    points?.let { player.setExperiencePoints(it) }
}

internal fun MinecraftServer.changeOperator(ref: String, operator: Boolean) {
    val id = player(ref).nameAndId()
    if (operator) playerList.op(id) else playerList.deop(id)
}

internal fun MinecraftServer.kick(ref: String, reason: String) {
    player(ref).connection.disconnect(textComponent(reason, registryAccess()))
}
