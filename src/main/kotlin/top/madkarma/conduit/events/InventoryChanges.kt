package top.madkarma.conduit.events

import java.util.*
import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.toInfo

// Hotbar, main inventory, armor and offhand, numbered like the player tools.
private const val SLOT_COUNT = Inventory.SLOT_OFFHAND + 1

/**
 * A slot of a player's inventory changed. Conduit compares inventories once per tick, so several
 * changes to one slot within a tick are one event, and a change undone within the tick never shows
 * up.
 */
@Serializable
data class InventoryChangeEvent(
    val player: String,
    val playerUuid: String,
    /** 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand. */
    val slot: Int,
    val oldItem: ItemInfo,
    val newItem: ItemInfo,
)

/**
 * Publishes INVENTORY_CHANGE by comparing each inventory with the previous tick. Commands, mods and
 * creative mode change inventories without a common hook, so comparing is the only way to see every
 * change.
 */
object InventoryChanges {
    // Contents of each player's inventory at the end of the previous tick, as copies.
    private val snapshots = HashMap<UUID, Array<ItemStack>>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::publishChanges)
        ServerPlayerEvents.LEAVE.register { player -> snapshots.remove(player.uuid) }
    }

    // Snapshots stay current even without listeners, so a new listener receives every change from
    // its first tick on.
    private fun publishChanges(server: MinecraftServer) {
        for (player in server.playerList.players) publishChanges(player)
    }

    private fun publishChanges(player: ServerPlayer) {
        val snapshot = snapshots[player.uuid]
        if (snapshot == null) {
            snapshots[player.uuid] = snapshotOf(player.inventory)
            return
        }

        for (slot in 0 until SLOT_COUNT) {
            val oldItem = snapshot[slot]
            val newItem = player.inventory.getItem(slot)

            if (ItemStack.matches(oldItem, newItem)) continue

            snapshot[slot] = newItem.copy()
            EventBus.publish(EventType.INVENTORY_CHANGE) {
                changePayload(player, slot, oldItem, newItem)
            }
        }
    }

    private fun snapshotOf(inventory: Inventory): Array<ItemStack> =
        Array(SLOT_COUNT) { slot -> inventory.getItem(slot).copy() }

    private fun changePayload(
        player: ServerPlayer,
        slot: Int,
        oldItem: ItemStack,
        newItem: ItemStack,
    ): InventoryChangeEvent {
        val registries = player.registryAccess()

        return InventoryChangeEvent(
            player = player.gameProfile.name,
            playerUuid = player.stringUUID,
            slot = slot,
            oldItem = oldItem.toInfo(registries),
            newItem = newItem.toInfo(registries),
        )
    }
}
