package top.madkarma.conduit

import kotlin.jvm.optionals.getOrNull
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import top.madkarma.conduit.config.GrpcConfig
import top.madkarma.conduit.events.GameEvents
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.service.server.trackDatapackReloads

object Conduit : ModInitializer {
    const val MOD_ID: String = "conduit"

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

        // Singleplayer worlds start and stop an integrated server each time the player opens one.
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            GameServer.current = server
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            if (GameServer.current === server) GameServer.current = null
        }
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
