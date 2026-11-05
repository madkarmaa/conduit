package top.madkarma.minecraft.grpc.client.service

import com.google.protobuf.Empty
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.arguments.EntityAnchorArgument
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.proto.ChatHistory
import top.madkarma.minecraft.grpc.proto.ChatHistoryRequest
import top.madkarma.minecraft.grpc.proto.ChatMessage
import top.madkarma.minecraft.grpc.proto.ClickSlotRequest
import top.madkarma.minecraft.grpc.proto.ClickWidgetRequest
import top.madkarma.minecraft.grpc.proto.ClientServiceGrpcKt
import top.madkarma.minecraft.grpc.proto.ClientState
import top.madkarma.minecraft.grpc.proto.ConnectRequest
import top.madkarma.minecraft.grpc.proto.HotbarSlot
import top.madkarma.minecraft.grpc.proto.Image
import top.madkarma.minecraft.grpc.proto.KeyList
import top.madkarma.minecraft.grpc.proto.PressKeyRequest
import top.madkarma.minecraft.grpc.proto.Rotation
import top.madkarma.minecraft.grpc.proto.ScreenInfo
import top.madkarma.minecraft.grpc.proto.SetKeyRequest
import top.madkarma.minecraft.grpc.proto.Vec3
import top.madkarma.minecraft.grpc.proto.chatHistory
import top.madkarma.minecraft.grpc.rpc.call
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.toStatusException

private val EMPTY: Empty = Empty.getDefaultInstance()

private const val SCREENSHOT_TIMEOUT_MS = 10_000L

class ClientServiceImpl : ClientServiceGrpcKt.ClientServiceCoroutineImplBase() {
    override suspend fun getState(request: Empty): ClientState = onClient { it.stateSnapshot() }

    override suspend fun setRotation(request: Rotation): Empty = withPlayer { _, player ->
        player.yRot = request.yaw
        player.xRot = request.pitch.coerceIn(-90f, 90f)
        player.yHeadRot = request.yaw
    }

    override suspend fun lookAt(request: Vec3): Empty =
        withPlayer { _, player -> player.lookAt(EntityAnchorArgument.Anchor.EYES, request.toMc()) }

    override suspend fun setKey(request: SetKeyRequest): Empty = act {
        KeyInput.set(KeyInput.mapping(request.key), request.down)
    }

    override suspend fun pressKey(request: PressKeyRequest): Empty = act {
        KeyInput.press(KeyInput.mapping(request.key), request.ticks)
    }

    override suspend fun releaseAllKeys(request: Empty): Empty = act { KeyInput.releaseAll() }

    override suspend fun listKeys(request: Empty): KeyList = onClient { KeyInput.describe(it) }

    override suspend fun selectHotbarSlot(request: HotbarSlot): Empty = withPlayer { _, player ->
        if (request.slot !in 0..8) invalidArgument("Hotbar slot must be between 0 and 8")
        player.inventory.selectedSlot = request.slot
    }

    override suspend fun attack(request: Empty): Empty = withPlayer { client, _ ->
        KeyInput.click(client.options.keyAttack)
    }

    override suspend fun use(request: Empty): Empty = withPlayer { client, _ ->
        KeyInput.click(client.options.keyUse)
    }

    override suspend fun sendChat(request: ChatMessage): Empty = withPlayer { _, player ->
        player.connection.sendChat(request.message)
    }

    override suspend fun sendCommand(request: ChatMessage): Empty =
        withPlayer { _, player -> player.connection.sendCommand(request.message.removePrefix("/")) }

    override suspend fun getScreen(request: Empty): ScreenInfo = onClient { it.screenSnapshot() }

    override suspend fun clickSlot(request: ClickSlotRequest): Empty =
        withPlayer { client, player -> client.clickSlot(player, request) }

    override suspend fun clickWidget(request: ClickWidgetRequest): Empty = act {
        it.clickWidget(request)
    }

    override suspend fun typeText(request: ChatMessage): Empty = act {
        it.typeText(request.message)
    }

    override suspend fun closeScreen(request: Empty): Empty = act { it.closeScreen() }

    override suspend fun openInventory(request: Empty): Empty = withPlayer { client, player ->
        client.openInventory(player)
    }

    override suspend fun respawn(request: Empty): Empty = withPlayer { _, player ->
        player.respawn()
    }

    override suspend fun screenshot(request: Empty): Image {
        val capture = onClient { it.captureScreenshot() }
        return try {
            withTimeout(SCREENSHOT_TIMEOUT_MS) { capture.await() }
        } catch (exception: Exception) {
            throw exception.toStatusException()
        }
    }

    override suspend fun connect(request: ConnectRequest): Empty = act {
        it.connect(request.address)
    }

    override suspend fun disconnect(request: Empty): Empty = act { it.disconnect() }

    override suspend fun getChatHistory(request: ChatHistoryRequest): ChatHistory =
        chatHistory { messages += ChatLog.recent(request.limit) }

    private suspend fun <T> onClient(block: (Minecraft) -> T): T {
        val client = Minecraft.getInstance()
        return client.call { block(client) }
    }

    private suspend fun act(block: (Minecraft) -> Unit): Empty = onClient { client ->
        block(client)
        EMPTY
    }

    private suspend fun withPlayer(block: (Minecraft, LocalPlayer) -> Unit): Empty = act { client ->
        block(client, client.player ?: failedPrecondition("Not in a world"))
    }
}
