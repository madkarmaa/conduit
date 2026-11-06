package top.madkarma.minecraft.grpc.service.player

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.service.entity.saveEntity
import java.util.*

internal fun MinecraftServer.offlinePlayer(request: PlayerRef): OfflinePlayerInfo {
    val id = resolvePlayerId(request.player)
    return describePlayerData(id, currentPlayerData(id), ProblemReporter.DISCARDING)
}

internal fun MinecraftServer.offlinePlayerNbt(request: PlayerRef): Nbt =
    nbt { snbt = currentPlayerData(resolvePlayerId(request.player)).toString() }

internal fun MinecraftServer.mergeOfflinePlayerNbt(request: MergeOfflinePlayerNbtRequest) {
    val patch = TagParser.parseCompoundFully(request.snbt)
    editPlayerData(request.player) { it.merge(patch) }
}

internal fun MinecraftServer.changeOfflineInventorySlot(request: SetInventorySlotRequest) {
    if (request.slot !in 0 until INVENTORY_SLOTS) {
        invalidArgument("Slot ${request.slot} is out of range")
    }

    val registries = registryAccess()
    val stack = request.item.toMc(registries)

    editPlayerData(request.player) { data ->
        val input = TagValueInput.create(ProblemReporter.DISCARDING, registries, data)
        val inventory = readInventory(input)
        inventory.setItem(request.slot, stack)
        writeInventory(data, inventory, registries)
    }
}

internal fun MinecraftServer.listKnownPlayers(): KnownPlayerList = knownPlayerList {
    for (id in knownPlayerIds()) {
        players += knownPlayer {
            uuid = id.toString()
            name = lastKnownName(id)
            lastPlayed = lastPlayed(id)
            online = isOnline(id)
        }
    }
}

// Online players are saved on demand, so reads always see their current state.
private fun MinecraftServer.currentPlayerData(id: UUID): CompoundTag =
    playerList.getPlayer(id)?.let { saveEntity(it) } ?: readPlayerData(id)

// The data is checked before and after the edit, so the file is only replaced with data the game
// can load completely.
private fun MinecraftServer.editPlayerData(ref: String, edit: (CompoundTag) -> Unit) {
    val id = resolvePlayerId(ref)
    requireOffline(id)

    val data = readPlayerData(id)
    problemsIn(id, data)?.let { failedPrecondition("Saved data of player $id is invalid: $it") }

    edit(data)
    problemsIn(id, data)?.let { invalidArgument("Edited player data is invalid: $it") }

    writePlayerData(id, data)
}
