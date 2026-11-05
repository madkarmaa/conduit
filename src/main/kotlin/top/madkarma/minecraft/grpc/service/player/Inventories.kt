package top.madkarma.minecraft.grpc.service.player

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Prediction
import net.minecraft.world.Container
import top.madkarma.minecraft.grpc.game.player
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument

internal fun MinecraftServer.inventoryOf(ref: String): Inventory {
    val inventory = player(ref).inventory
    return contents(inventory, inventory.selectedSlot)
}

internal fun MinecraftServer.enderChestOf(ref: String): Inventory =
    contents(player(ref).enderChestInventory, 0)

internal fun MinecraftServer.changeInventorySlot(request: SetInventorySlotRequest) {
    val inventory = player(request.player).inventory

    if (request.slot !in 0 until inventory.containerSize) {
        invalidArgument("Slot ${request.slot} is out of range")
    }

    inventory.setItem(request.slot, request.item.toMc(registryAccess()))
}

/** Returns how many items did not fit and were dropped at the player's feet. */
internal fun MinecraftServer.giveItem(request: GiveItemRequest): Int {
    val player = player(request.player)
    val stack = request.item.toMc(registryAccess())

    player.inventory.add(stack)

    val dropped = stack.count
    if (!stack.isEmpty) player.drop(stack, false, Prediction.SERVER_ONLY)

    return dropped
}

internal fun MinecraftServer.selectHotbarSlot(request: SetSelectedSlotRequest) {
    if (request.slot !in 0..8) invalidArgument("Hotbar slot must be between 0 and 8")
    val player = player(request.player)

    player.inventory.selectedSlot = request.slot
    player.connection.send(ClientboundSetHeldSlotPacket(request.slot))
}

private fun MinecraftServer.contents(container: Container, selected: Int): Inventory {
    val registries = registryAccess()

    return inventory {
        size = container.containerSize
        selectedSlot = selected

        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (stack.isEmpty) continue

            slots += inventorySlot {
                this.slot = slot
                item = stack.toProto(registries)
            }
        }
    }
}
