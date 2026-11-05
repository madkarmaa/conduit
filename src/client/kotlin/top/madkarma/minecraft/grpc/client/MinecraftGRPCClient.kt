package top.madkarma.minecraft.grpc.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import top.madkarma.minecraft.grpc.MinecraftGRPC
import top.madkarma.minecraft.grpc.client.service.ChatLog
import top.madkarma.minecraft.grpc.client.service.ClientServiceImpl
import top.madkarma.minecraft.grpc.client.service.KeyInput
import top.madkarma.minecraft.grpc.rpc.GrpcHost

object MinecraftGRPCClient : ClientModInitializer {
    override fun onInitializeClient() {
        ChatLog.register()
        ClientTickEvents.START_CLIENT_TICK.register { KeyInput.startTick() }
        ClientTickEvents.END_CLIENT_TICK.register { KeyInput.endTick() }

        GrpcHost.register(ClientServiceImpl())
        MinecraftGRPC.startGrpc()
        ClientLifecycleEvents.CLIENT_STOPPING.register { GrpcHost.stop() }
    }
}
