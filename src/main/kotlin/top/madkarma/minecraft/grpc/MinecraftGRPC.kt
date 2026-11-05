package top.madkarma.minecraft.grpc

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import top.madkarma.minecraft.grpc.config.GrpcConfig
import top.madkarma.minecraft.grpc.events.GameEvents
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.rpc.GrpcHost
import top.madkarma.minecraft.grpc.service.entity.EntityServiceImpl
import top.madkarma.minecraft.grpc.service.events.EventServiceImpl
import top.madkarma.minecraft.grpc.service.player.PlayerServiceImpl
import top.madkarma.minecraft.grpc.service.server.ServerServiceImpl
import top.madkarma.minecraft.grpc.service.world.WorldServiceImpl

object MinecraftGRPC : ModInitializer {
    const val MOD_ID: String = "minecraft_grpc"

    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    val version: String by lazy {
        FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.version.friendlyString }.orElse("unknown")
    }

    val config: GrpcConfig by lazy { GrpcConfig.load() }

    override fun onInitialize() {
        GameEvents.register()

        GrpcHost.register(
            ServerServiceImpl(),
            WorldServiceImpl(),
            EntityServiceImpl(),
            PlayerServiceImpl(),
            EventServiceImpl(),
        )

        // Singleplayer worlds start and stop an integrated server each time they are opened.
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            GameServer.current = server
            startGrpc()
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            GrpcHost.stop()
            if (GameServer.current === server) GameServer.current = null
        }
    }

    private fun startGrpc() {
        try {
            GrpcHost.start(config)
        } catch (exception: Exception) {
            LOGGER.error(
                "Failed to start the gRPC server on {}:{}",
                config.host,
                config.port,
                exception,
            )
        }
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
