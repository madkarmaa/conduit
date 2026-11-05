package top.madkarma.minecraft.grpc.client.service

import com.google.protobuf.ByteString
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import top.madkarma.minecraft.grpc.proto.Image
import top.madkarma.minecraft.grpc.proto.image
import top.madkarma.minecraft.grpc.rpc.failedPrecondition
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

internal fun Minecraft.connect(address: String) {
    if (!ServerAddress.isValidAddress(address)) invalidArgument("Invalid server address: $address")
    leaveWorld()
    val server = ServerData("gRPC", address, ServerData.Type.OTHER)
    ConnectScreen.startConnecting(
        TitleScreen(),
        this,
        ServerAddress.parseString(address),
        server,
        false,
        null,
    )
}

internal fun Minecraft.disconnect() {
    if (level == null) failedPrecondition("Not in a world")
    leaveWorld()
    gui.setScreen(TitleScreen())
}

// The PNG is encoded off the client thread to keep the game responsive.
internal fun Minecraft.captureScreenshot(): CompletableFuture<Image> {
    val result = CompletableFuture<Image>()
    Screenshot.takeScreenshot(gameRenderer.mainRenderTarget()) { frame ->
        result.completeAsync { encodePng(frame) }
    }
    return result
}

// Saves singleplayer worlds before leaving them.
private fun Minecraft.leaveWorld() {
    if (level != null) disconnectWithSavingScreen()
}

private fun encodePng(frame: NativeImage): Image = frame.use {
    val file = Files.createTempFile("minecraft-grpc-", ".png")
    try {
        try {
            frame.writeToFile(file)
        } catch (exception: IOException) {
            // Clients that do not render (e.g. HeadlessMC with -lwjgl) produce empty frames.
            failedPrecondition(
                "Could not capture a frame, the client is not rendering: ${exception.message}",
            )
        }
        image {
            png = ByteString.copyFrom(Files.readAllBytes(file))
            width = frame.width
            height = frame.height
        }
    } finally {
        Files.deleteIfExists(file)
    }
}
