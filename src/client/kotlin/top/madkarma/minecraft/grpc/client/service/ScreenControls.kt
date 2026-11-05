package top.madkarma.minecraft.grpc.client.service

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.inventory.ContainerInput
import top.madkarma.minecraft.grpc.game.enumByName
import top.madkarma.minecraft.grpc.proto.ClickSlotRequest
import top.madkarma.minecraft.grpc.proto.ClickWidgetRequest
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument

// Slot id the game uses for clicks outside the container window.
private const val OUTSIDE_SLOT = -999

internal fun Minecraft.clickSlot(player: LocalPlayer, request: ClickSlotRequest) {
    val screen =
        gui.screen() as? AbstractContainerScreen<*>
            ?: failedPrecondition("No container screen is open")
    val menu = screen.menu
    if (request.slot != OUTSIDE_SLOT && request.slot !in menu.slots.indices) {
        invalidArgument("Slot ${request.slot} is out of range")
    }
    gameMode!!.handleContainerInput(
        menu.containerId,
        request.slot,
        request.button,
        enumByName(request.action, ContainerInput.PICKUP, "slot action"),
        player,
    )
}

internal fun Minecraft.clickWidget(request: ClickWidgetRequest) {
    val screen = gui.screen() ?: failedPrecondition("No screen is open")
    val widget =
        widgetsOf(screen).getOrNull(request.index)
            ?: invalidArgument("No widget with index ${request.index}")
    if (!widget.active || !widget.visible) {
        failedPrecondition("Widget ${request.index} is not active")
    }
    val centerX = widget.x + widget.width / 2.0
    val centerY = widget.y + widget.height / 2.0
    val event = MouseButtonEvent(centerX, centerY, MouseButtonInfo(request.button, 0))
    // The widget handles the click itself: screen-level dispatch relies on hover state that is only
    // updated while rendering, which headless clients never do.
    screen.focused = widget
    widget.onClick(event, false)
    widget.onRelease(event)
}

internal fun Minecraft.typeText(text: String) {
    val field = gui.screen()?.focused as? EditBox ?: failedPrecondition("No text field is focused")
    field.insertText(text)
}

// Container screens are closed through the player so the server is told as well.
internal fun Minecraft.closeScreen() {
    val player = player
    if (player == null || gui.screen() !is AbstractContainerScreen<*>) {
        gui.setScreen(null)
        return
    }
    player.closeContainer()
}

internal fun Minecraft.openInventory(player: LocalPlayer) {
    gui.setScreen(InventoryScreen(player))
}
