package top.madkarma.minecraft.grpc.service.player

import com.google.protobuf.Empty
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.game.player
import top.madkarma.minecraft.grpc.game.toComponent
import top.madkarma.minecraft.grpc.proto.GiveItemRequest
import top.madkarma.minecraft.grpc.proto.GiveItemResponse
import top.madkarma.minecraft.grpc.proto.Inventory
import top.madkarma.minecraft.grpc.proto.KickRequest
import top.madkarma.minecraft.grpc.proto.PlaySoundRequest
import top.madkarma.minecraft.grpc.proto.PlayerInfo
import top.madkarma.minecraft.grpc.proto.PlayerList
import top.madkarma.minecraft.grpc.proto.PlayerRef
import top.madkarma.minecraft.grpc.proto.PlayerServiceGrpcKt
import top.madkarma.minecraft.grpc.proto.SendMessageRequest
import top.madkarma.minecraft.grpc.proto.SendTitleRequest
import top.madkarma.minecraft.grpc.proto.SetExperienceRequest
import top.madkarma.minecraft.grpc.proto.SetFoodRequest
import top.madkarma.minecraft.grpc.proto.SetGameModeRequest
import top.madkarma.minecraft.grpc.proto.SetInventorySlotRequest
import top.madkarma.minecraft.grpc.proto.SetOperatorRequest
import top.madkarma.minecraft.grpc.proto.SetSelectedSlotRequest
import top.madkarma.minecraft.grpc.proto.giveItemResponse
import top.madkarma.minecraft.grpc.proto.playerList

private val EMPTY: Empty = Empty.getDefaultInstance()

class PlayerServiceImpl : PlayerServiceGrpcKt.PlayerServiceCoroutineImplBase() {
    override suspend fun listPlayers(request: Empty): PlayerList = GameServer.run { server ->
        playerList {
            players +=
                server.playerList.players.map { server.describePlayer(it) }
        }
    }

    override suspend fun getPlayer(request: PlayerRef): PlayerInfo =
        GameServer.run { it.describePlayer(it.player(request.player)) }

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

    override suspend fun giveItem(request: GiveItemRequest): GiveItemResponse =
        GameServer.run { server -> giveItemResponse { dropped = server.giveItem(request) } }

    override suspend fun clearInventory(request: PlayerRef): Empty = GameServer.run {
        it.player(request.player).inventory.clearContent()
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

    override suspend fun sendMessage(request: SendMessageRequest): Empty =
        GameServer.run { server ->
            val message = request.message.toComponent(server.registryAccess())
            server.player(request.player).sendSystemMessage(message, request.overlay)
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

    override suspend fun kick(request: KickRequest): Empty = GameServer.run { server ->
        server.player(
            request.player,
        ).connection.disconnect(request.reason.toComponent(server.registryAccess()))
        EMPTY
    }

    override suspend fun setOperator(request: SetOperatorRequest): Empty = GameServer.run {
        it.changeOperator(request)
        EMPTY
    }
}
