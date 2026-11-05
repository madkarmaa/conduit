package top.madkarma.minecraft.grpc.service.player

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import top.madkarma.minecraft.grpc.game.player
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument

internal fun MinecraftServer.describePlayer(player: ServerPlayer): PlayerInfo {
    val registries = registryAccess()

    return playerInfo {
        name = player.gameProfile.name
        entity = player.toProto()
        gameMode = player.gameMode().serializedName
        foodLevel = player.foodData.foodLevel
        saturation = player.foodData.saturationLevel
        experienceLevel = player.experienceLevel
        experienceProgress = player.experienceProgress
        totalExperience = player.totalExperience
        selectedSlot = player.inventory.selectedSlot
        pingMs = player.connection.latency()
        operator = playerList.isOp(player.nameAndId())
        ipAddress = player.ipAddress
        mainHand = player.mainHandItem.toProto(registries)
        offHand = player.offhandItem.toProto(registries)
    }
}

internal fun MinecraftServer.changeGameMode(request: SetGameModeRequest) {
    val mode = GameType.byName(request.gameMode.lowercase(), null)
        ?: invalidArgument("Unknown game mode: ${request.gameMode}")

    player(request.player).setGameMode(mode)
}

internal fun MinecraftServer.changeFood(request: SetFoodRequest) {
    val food = player(request.player).foodData

    if (request.hasFoodLevel()) food.foodLevel = request.foodLevel
    if (request.hasSaturation()) food.setSaturation(request.saturation)
}

internal fun MinecraftServer.changeExperience(request: SetExperienceRequest) {
    val player = player(request.player)

    if (request.hasLevels()) player.setExperienceLevels(request.levels)
    if (request.hasPoints()) player.setExperiencePoints(request.points)
}

internal fun MinecraftServer.changeOperator(request: SetOperatorRequest) {
    val id = player(request.player).nameAndId()
    if (request.operator) playerList.op(id) else playerList.deop(id)
}
