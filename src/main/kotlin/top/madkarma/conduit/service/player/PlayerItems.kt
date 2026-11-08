package top.madkarma.conduit.service.player

import java.util.UUID
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.PlayerEnderChestContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.TagValueInput
import top.madkarma.conduit.game.UndoableChange

/**
 * A player's inventory and ender chest before a recorded call changed them. Undo puts the items
 * back whether the player is online or offline by then. Slot numbers follow the online inventory.
 */
class PlayerItems
private constructor(
    private val server: MinecraftServer,
    val id: UUID,
    private val inventory: List<ItemStack>,
    private val enderChest: List<ItemStack>,
) : UndoableChange {
    override fun changed(): Boolean {
        val now = of(server, id)
        return !sameItems(inventory, now.inventory) || !sameItems(enderChest, now.enderChest)
    }

    override fun overlaps(other: UndoableChange): Boolean = other is PlayerItems && other.id == id

    override fun undo(server: MinecraftServer) {
        val player = server.playerList.getPlayer(id)
        if (player != null) {
            fill(player.inventory, inventory)
            fill(player.enderChestInventory, enderChest)
            return
        }

        server.restoreSavedItems(id, inventory, enderChest)
    }

    companion object {
        /** The player's items now, from the online player or else from the saved data. */
        fun of(server: MinecraftServer, id: UUID): PlayerItems {
            val player = server.playerList.getPlayer(id)
            if (player != null) {
                return PlayerItems(
                    server,
                    id,
                    copies(player.inventory),
                    copies(player.enderChestInventory),
                )
            }

            val input =
                TagValueInput.create(
                    ProblemReporter.DISCARDING,
                    server.registryAccess(),
                    server.readPlayerData(id),
                )
            return PlayerItems(
                server,
                id,
                copies(readInventory(input)),
                copies(readEnderChest(input)),
            )
        }
    }
}

private fun copies(container: Container): List<ItemStack> =
    (0 until container.containerSize).map { container.getItem(it).copy() }

private fun sameItems(first: List<ItemStack>, second: List<ItemStack>): Boolean =
    first.size == second.size && first.indices.all { ItemStack.matches(first[it], second[it]) }

private fun fill(container: Container, items: List<ItemStack>) {
    items.forEachIndexed { slot, stack -> container.setItem(slot, stack.copy()) }
    container.setChanged()
}

private fun MinecraftServer.restoreSavedItems(
    id: UUID,
    inventory: List<ItemStack>,
    enderChest: List<ItemStack>,
) {
    requireOffline(id)
    val data = readPlayerData(id)
    val registries = registryAccess()

    val items = SimpleContainer(INVENTORY_SLOTS)
    fill(items, inventory)
    writeInventory(data, items, registries)

    val chest = PlayerEnderChestContainer()
    fill(chest, enderChest)
    writeEnderChest(data, chest, registries)
    writePlayerData(id, data)
}
