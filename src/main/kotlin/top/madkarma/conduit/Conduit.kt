package top.madkarma.conduit

import kotlin.jvm.optionals.getOrNull
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import top.madkarma.conduit.config.GrpcConfig
import top.madkarma.conduit.events.GameEvents
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.rpc.GrpcHost
import top.madkarma.conduit.service.batch.BatchServiceImpl
import top.madkarma.conduit.service.entity.EntityServiceImpl
import top.madkarma.conduit.service.events.EventServiceImpl
import top.madkarma.conduit.service.player.PlayerServiceImpl
import top.madkarma.conduit.service.registry.RegistryServiceImpl
import top.madkarma.conduit.service.server.ServerServiceImpl
import top.madkarma.conduit.service.server.trackDatapackReloads
import top.madkarma.conduit.service.structure.StructureServiceImpl
import top.madkarma.conduit.service.world.WorldServiceImpl

object Conduit : ModInitializer {
    const val MOD_ID: String = "conduit"

    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    val version: String by lazy {
        FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .getOrNull()
            ?.metadata
            ?.version
            ?.friendlyString ?: "unknown"
    }

    val config: GrpcConfig by lazy { GrpcConfig.load() }

    override fun onInitialize() {
        GameEvents.register()
        trackDatapackReloads()

        GrpcHost.register(
            ServerServiceImpl(),
            WorldServiceImpl(),
            EntityServiceImpl(),
            PlayerServiceImpl(),
            EventServiceImpl(),
            RegistryServiceImpl(),
            BatchServiceImpl(),
            StructureServiceImpl(),
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
