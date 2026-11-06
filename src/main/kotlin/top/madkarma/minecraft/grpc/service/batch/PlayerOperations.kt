package top.madkarma.minecraft.grpc.service.batch

import net.minecraft.server.MinecraftServer
import top.madkarma.minecraft.grpc.proto.Operation
import top.madkarma.minecraft.grpc.proto.Operation.OperationCase.*
import top.madkarma.minecraft.grpc.proto.OperationResult
import top.madkarma.minecraft.grpc.service.player.*

/** Returns false when the operation is not a PlayerService operation. */
internal fun MinecraftServer.applyPlayerOperation(
    operation: Operation,
    result: OperationResult.Builder,
): Boolean {
    when (operation.operationCase) {
        LIST_PLAYERS -> result.players = listPlayers()
        GET_PLAYER -> result.player = describePlayer(operation.getPlayer)
        GET_INVENTORY -> result.inventory = inventoryOf(operation.getInventory.player)
        SET_INVENTORY_SLOT -> changeInventorySlot(operation.setInventorySlot)
        GIVE_ITEM -> result.giveItem = giveItem(operation.giveItem)
        CLEAR_INVENTORY -> clearInventory(operation.clearInventory)
        SET_SELECTED_SLOT -> selectHotbarSlot(operation.setSelectedSlot)
        GET_ENDER_CHEST -> result.inventory = enderChestOf(operation.getEnderChest.player)
        SET_GAME_MODE -> changeGameMode(operation.setGameMode)
        SET_FOOD -> changeFood(operation.setFood)
        SET_EXPERIENCE -> changeExperience(operation.setExperience)
        SEND_MESSAGE -> sendMessage(operation.sendMessage)
        SEND_TITLE -> sendTitle(operation.sendTitle)
        PLAY_SOUND -> playSound(operation.playSound)
        KICK -> kick(operation.kick)
        SET_OPERATOR -> changeOperator(operation.setOperator)
        GET_OFFLINE_PLAYER -> result.offlinePlayer = offlinePlayer(operation.getOfflinePlayer)
        GET_OFFLINE_PLAYER_NBT -> result.nbt = offlinePlayerNbt(operation.getOfflinePlayerNbt)
        MERGE_OFFLINE_PLAYER_NBT -> mergeOfflinePlayerNbt(operation.mergeOfflinePlayerNbt)
        SET_OFFLINE_INVENTORY_SLOT -> changeOfflineInventorySlot(operation.setOfflineInventorySlot)
        GET_PLAYER_STATS -> result.playerStats = playerStats(operation.getPlayerStats)
        LIST_KNOWN_PLAYERS -> result.knownPlayers = listKnownPlayers()
        else -> return false
    }

    return true
}
