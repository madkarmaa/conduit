package top.madkarma.conduit

import kotlin.jvm.optionals.getOrNull
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.ConfigFile
import top.madkarma.conduit.config.GameConfig
import top.madkarma.conduit.config.InvalidConfig
import top.madkarma.conduit.events.GameEvents
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.AccessTokens
import top.madkarma.conduit.mcp.McpHost
import top.madkarma.conduit.service.ALL_TOOLS
import top.madkarma.conduit.service.server.trackDatapackReloads

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

    /** The last valid config, or null while the config file has never been valid. */
    @Volatile
    var config: ConduitConfig? = null
        private set

    val gameConfig: GameConfig
        get() = config?.game ?: GameConfig()

    override fun onInitialize() {
        GameEvents.register()
        trackDatapackReloads()
        config = readConfig()

        // Singleplayer worlds start and stop an integrated server each time the player opens one.
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            GameServer.current = server
            config?.let(::startMcp)
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            McpHost.stop()
            if (GameServer.current === server) GameServer.current = null
        }
    }

    private fun readConfig(): ConduitConfig? =
        try {
            ConfigFile.read(ALL_TOOLS.map { it.name }.toSet())
        } catch (exception: InvalidConfig) {
            LOGGER.error(
                "Invalid {}, the MCP server stays off until it is fixed: {}",
                ConfigFile.path,
                exception.problems.joinToString("; "),
            )
            null
        }

    private fun startMcp(config: ConduitConfig) {
        AccessTokens.configure(config.mcp.tokens)

        try {
            McpHost.apply(config.mcp, ALL_TOOLS, version)
        } catch (exception: Exception) {
            LOGGER.error(
                "Failed to start the MCP server on {}:{}",
                config.mcp.host,
                config.mcp.port,
                exception,
            )
        }
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
