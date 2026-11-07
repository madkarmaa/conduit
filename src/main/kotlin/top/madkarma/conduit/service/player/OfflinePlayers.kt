package top.madkarma.conduit.service.player

import java.util.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.proto.*
import top.madkarma.conduit.service.entity.saveEntity

internal fun MinecraftServer.offlinePlayer(request: PlayerRef): OfflinePlayerInfo {
    val id = resolvePlayerId(request.player)
    return describePlayerData(id, currentPlayerData(id), ProblemReporter.DISCARDING)
}

internal fun MinecraftServer.offlinePlayerNbt(request: PlayerRef): Nbt = nbt {
    snbt = currentPlayerData(resolvePlayerId(request.player)).toString()
}

internal fun MinecraftServer.mergeOfflinePlayerNbt(request: MergeOfflinePlayerNbtRequest) {
    val patch = TagParser.parseCompoundFully(request.snbt)
    editPlayerData(request.player) { it.merge(patch) }
}

internal fun MinecraftServer.changeOfflineInventorySlot(request: SetInventorySlotRequest) {
    if (request.slot !in 0 until INVENTORY_SLOTS) {
        invalidInput("Slot ${request.slot} is out of range")
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

// Reads save online players first, so they always see the current state.
private fun MinecraftServer.currentPlayerData(id: UUID): CompoundTag =
    playerList.getPlayer(id)?.let { saveEntity(it) } ?: readPlayerData(id)

// Edits check the data before and after the change and only replace the file with data the game
// can load completely.
private fun MinecraftServer.editPlayerData(ref: String, edit: (CompoundTag) -> Unit) {
    val id = resolvePlayerId(ref)
    requireOffline(id)

    val data = readPlayerData(id)
    problemsIn(id, data)?.let { failedPrecondition("Saved data of player $id is invalid: $it") }

    edit(data)
    problemsIn(id, data)?.let { invalidInput("Edited player data is invalid: $it") }

    writePlayerData(id, data)
}
