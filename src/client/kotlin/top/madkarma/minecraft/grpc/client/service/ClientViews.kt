package top.madkarma.minecraft.grpc.client.service

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import top.madkarma.minecraft.grpc.game.rotationOf
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.ClientState
import top.madkarma.minecraft.grpc.proto.ContainerInfo
import top.madkarma.minecraft.grpc.proto.ScreenInfo
import top.madkarma.minecraft.grpc.proto.Target
import top.madkarma.minecraft.grpc.proto.WidgetInfo
import top.madkarma.minecraft.grpc.proto.blockTarget
import top.madkarma.minecraft.grpc.proto.clientState
import top.madkarma.minecraft.grpc.proto.containerInfo
import top.madkarma.minecraft.grpc.proto.containerSlot
import top.madkarma.minecraft.grpc.proto.copy
import top.madkarma.minecraft.grpc.proto.screenInfo
import top.madkarma.minecraft.grpc.proto.target
import top.madkarma.minecraft.grpc.proto.widgetInfo

fun Minecraft.stateSnapshot(): ClientState {
    val client = this
    val session = clientState {
        username = client.user.name
        playerUuid = client.user.profileId.toString()
        screen = client.gui.screen()?.javaClass?.name ?: ""
        fps = client.fps
        paused = client.isPaused
        singleplayer = client.isLocalServer
        client.currentServer?.let { serverAddress = it.ip }
    }
    val player = client.player ?: return session
    return session.copy {
        inWorld = client.level != null
        playerUuid = player.stringUUID
        dimension = player.level().dimension().identifier().toString()
        position = player.position().toProto()
        rotation = rotationOf(player.yRot, player.xRot)
        velocity = player.deltaMovement.toProto()
        health = player.health
        foodLevel = player.foodData.foodLevel
        experienceLevel = player.experienceLevel
        client.gameMode?.playerMode?.let { gameMode = it.serializedName }
        selectedSlot = player.inventory.selectedSlot
        mainHand = player.mainHandItem.toProto(player.registryAccess())
        onGround = player.onGround()
        dead = player.isDeadOrDying
        client.hitResult?.let { hit -> targetOf(client, hit)?.let { target = it } }
    }
}

private fun targetOf(client: Minecraft, hit: HitResult): Target? = when {
    hit.type == HitResult.Type.MISS -> null

    hit is BlockHitResult -> target {
        block = blockTarget {
            pos = hit.blockPos.toProto()
            face = hit.direction.serializedName
            state = client.level!!.getBlockState(hit.blockPos).toProto()
            location = hit.location.toProto()
        }
    }

    hit is EntityHitResult -> target { entity = hit.entity.toProto() }

    else -> null
}

fun Minecraft.screenSnapshot(): ScreenInfo {
    val screen = gui.screen() ?: return ScreenInfo.getDefaultInstance()
    val registries = player?.registryAccess()
    return screenInfo {
        type = screen.javaClass.name
        title = screen.title.string
        width = screen.width
        height = screen.height
        if (screen is AbstractContainerScreen<*> && registries != null) {
            container = containerSnapshot(screen.menu, registries)
        }
        widgets +=
            widgetsOf(screen).mapIndexed { index, widget -> widgetSnapshot(index, widget, screen) }
    }
}

fun widgetsOf(screen: Screen): List<AbstractWidget> =
    screen.children().filterIsInstance<AbstractWidget>()

private fun widgetSnapshot(index: Int, widget: AbstractWidget, screen: Screen): WidgetInfo =
    widgetInfo {
        this.index = index
        type = widget.javaClass.name
        message = widget.message.string
        x = widget.x
        y = widget.y
        width = widget.width
        height = widget.height
        active = widget.active
        visible = widget.visible
        focused = screen.focused === widget
        if (widget is EditBox) value = widget.value
    }

private fun containerSnapshot(
    menu: AbstractContainerMenu,
    registries: net.minecraft.core.HolderLookup.Provider,
): ContainerInfo = containerInfo {
    containerId = menu.containerId
    menuType =
        runCatching { BuiltInRegistries.MENU.getKey(menu.type)?.toString() }.getOrNull()
            ?: menu.javaClass.name
    carried = menu.carried.toProto(registries)
    for (slot in menu.slots) {
        slots += containerSlot {
            index = slot.index
            item = slot.item.toProto(registries)
            playerInventory = slot.container is Inventory
            containerSlot = slot.containerSlot
        }
    }
}
