package top.madkarma.conduit.service.player

import java.util.*
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.TagValueInput
import top.madkarma.conduit.game.*
import top.madkarma.conduit.service.entity.saveEntity

// Saved player data works for offline players too. Reads of online players return their live
// data. Edits of online or joining players fail, and the others take effect at the next login.

@Serializable
data class KnownPlayer(
    val uuid: String,
    /** Last known name, empty if the server does not know it. */
    val name: String,
    /** Unix milliseconds of the last save, or the current time for online players. */
    val lastPlayed: Long,
    val online: Boolean,
)

internal fun MinecraftServer.offlinePlayer(ref: String): OfflinePlayerInfo {
    val id = resolvePlayerId(ref)
    return describePlayerData(id, currentPlayerData(id), ProblemReporter.DISCARDING)
}

/** The full saved data as SNBT, updated to the current game version. */
internal fun MinecraftServer.offlinePlayerNbt(ref: String): String =
    currentPlayerData(resolvePlayerId(ref)).toString()

/** Merges SNBT into the saved data, like /data merge. */
internal fun MinecraftServer.mergeOfflinePlayerNbt(ref: String, snbt: String) {
    val patch = TagParser.parseCompoundFully(snbt)
    editPlayerData(ref) { it.merge(patch) }
}

/**
 * Uses the same slot numbers as the online inventory, or the ender chest's 0-26. A null item
 * empties the slot.
 */
internal fun MinecraftServer.changeOfflineInventorySlot(
    ref: String,
    slot: Int,
    item: ItemInfo?,
    enderChest: Boolean,
) {
    val size = if (enderChest) ENDER_CHEST_SLOTS else INVENTORY_SLOTS
    if (slot !in 0 until size) invalidInput("Slot $slot is out of range")

    val registries = registryAccess()
    val stack = item?.toMc(registries) ?: ItemStack.EMPTY

    editPlayerData(ref) { data ->
        val input = TagValueInput.create(ProblemReporter.DISCARDING, registries, data)
        if (enderChest) {
            val chest = readEnderChest(input).also { it.setItem(slot, stack) }
            writeEnderChest(data, chest, registries)
        } else {
            val inventory = readInventory(input).also { it.setItem(slot, stack) }
            writeInventory(data, inventory, registries)
        }
    }
}

/** Every player with saved data. */
internal fun MinecraftServer.listKnownPlayers(): List<KnownPlayer> =
    knownPlayerIds().map { id ->
        KnownPlayer(
            uuid = id.toString(),
            name = lastKnownName(id),
            lastPlayed = lastPlayed(id),
            online = isOnline(id),
        )
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
    EditJournal.before { PlayerItems.of(this, id) }
    EditJournal.before { PlayerSnapshot.saved(this, id, data) }

    edit(data)
    problemsIn(id, data)?.let { invalidInput("Edited player data is invalid: $it") }

    writePlayerData(id, data)
}
