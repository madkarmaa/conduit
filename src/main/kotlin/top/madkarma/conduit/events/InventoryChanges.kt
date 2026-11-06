package top.madkarma.conduit.events

import java.util.*
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.toProto
import top.madkarma.conduit.proto.EventType
import top.madkarma.conduit.proto.InventoryChangeEvent
import top.madkarma.conduit.proto.inventoryChangeEvent

// Hotbar, main inventory, armor and offhand, numbered like PlayerService.
private const val SLOT_COUNT = Inventory.SLOT_OFFHAND + 1

/**
 * Publishes EVENT_TYPE_INVENTORY_CHANGE by comparing each inventory with the previous tick.
 * Commands, mods and creative mode change inventories without a common hook, so comparing is the
 * only way to see every change.
 */
object InventoryChanges {
    // Contents of each player's inventory at the end of the previous tick, as copies.
    private val snapshots = HashMap<UUID, Array<ItemStack>>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::publishChanges)
        ServerPlayerEvents.LEAVE.register { player -> snapshots.remove(player.uuid) }
    }

    // Snapshots stay current even without subscribers: a new subscription then sees every change
    // from its first tick, instead of one tick being taken as the starting point.
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
            EventBus.publish(
                EventType.EVENT_TYPE_INVENTORY_CHANGE,
                accepts = { it.wantsPlayer(player) },
            ) {
                inventoryChange = changePayload(player, slot, oldItem, newItem)
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
    ): InventoryChangeEvent = inventoryChangeEvent {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID
        this.slot = slot

        val registries = player.registryAccess()
        this.oldItem = oldItem.toProto(registries)
        this.newItem = newItem.toProto(registries)
    }
}
