package top.madkarma.conduit.service.player

import com.google.protobuf.Empty
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.proto.*

private val EMPTY: Empty = Empty.getDefaultInstance()

class PlayerServiceImpl : PlayerServiceGrpcKt.PlayerServiceCoroutineImplBase() {
    override suspend fun listPlayers(request: Empty): PlayerList = GameServer.run {
        it.listPlayers()
    }

    override suspend fun getPlayer(request: PlayerRef): PlayerInfo = GameServer.run {
        it.describePlayer(request)
    }

    override suspend fun getInventory(request: PlayerRef): Inventory = GameServer.run {
        it.inventoryOf(request.player)
    }

    override suspend fun getEnderChest(request: PlayerRef): Inventory = GameServer.run {
        it.enderChestOf(request.player)
    }

    override suspend fun setInventorySlot(request: SetInventorySlotRequest): Empty =
        GameServer.run {
            it.changeInventorySlot(request)
            EMPTY
        }

    override suspend fun giveItem(request: GiveItemRequest): GiveItemResponse = GameServer.run {
        it.giveItem(request)
    }

    override suspend fun clearInventory(request: PlayerRef): Empty = GameServer.run {
        it.clearInventory(request)
        EMPTY
    }

    override suspend fun setSelectedSlot(request: SetSelectedSlotRequest): Empty = GameServer.run {
        it.selectHotbarSlot(request)
        EMPTY
    }

    override suspend fun setGameMode(request: SetGameModeRequest): Empty = GameServer.run {
        it.changeGameMode(request)
        EMPTY
    }

    override suspend fun setFood(request: SetFoodRequest): Empty = GameServer.run {
        it.changeFood(request)
        EMPTY
    }

    override suspend fun setExperience(request: SetExperienceRequest): Empty = GameServer.run {
        it.changeExperience(request)
        EMPTY
    }

    override suspend fun sendMessage(request: SendMessageRequest): Empty = GameServer.run {
        it.sendMessage(request)
        EMPTY
    }

    override suspend fun sendTitle(request: SendTitleRequest): Empty = GameServer.run {
        it.sendTitle(request)
        EMPTY
    }

    override suspend fun playSound(request: PlaySoundRequest): Empty = GameServer.run {
        it.playSound(request)
        EMPTY
    }

    override suspend fun kick(request: KickRequest): Empty = GameServer.run {
        it.kick(request)
        EMPTY
    }

    override suspend fun setOperator(request: SetOperatorRequest): Empty = GameServer.run {
        it.changeOperator(request)
        EMPTY
    }

    override suspend fun getOfflinePlayer(request: PlayerRef): OfflinePlayerInfo = GameServer.run {
        it.offlinePlayer(request)
    }

    override suspend fun getOfflinePlayerNbt(request: PlayerRef): Nbt = GameServer.run {
        it.offlinePlayerNbt(request)
    }

    override suspend fun mergeOfflinePlayerNbt(request: MergeOfflinePlayerNbtRequest): Empty =
        GameServer.run {
            it.mergeOfflinePlayerNbt(request)
            EMPTY
        }

    override suspend fun setOfflineInventorySlot(request: SetInventorySlotRequest): Empty =
        GameServer.run {
            it.changeOfflineInventorySlot(request)
            EMPTY
        }

    override suspend fun getPlayerStats(request: PlayerRef): PlayerStats = GameServer.run {
        it.playerStats(request)
    }

    override suspend fun listKnownPlayers(request: Empty): KnownPlayerList = GameServer.run {
        it.listKnownPlayers()
    }
}
