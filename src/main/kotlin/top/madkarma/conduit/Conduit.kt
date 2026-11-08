package top.madkarma.conduit

import kotlin.jvm.optionals.getOrNull
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import top.madkarma.conduit.bot.Bot
import top.madkarma.conduit.bot.ConduitCommand
import top.madkarma.conduit.bot.Triggers
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.ConfigFile
import top.madkarma.conduit.config.GameConfig
import top.madkarma.conduit.config.InvalidConfig
import top.madkarma.conduit.events.GameEvents
import top.madkarma.conduit.game.EditJournal
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.AccessTokens
import top.madkarma.conduit.mcp.McpHost
import top.madkarma.conduit.service.ALL_TOOLS
import top.madkarma.conduit.service.server.trackDatapackReloads

object Conduit : ModInitializer {
    const val MOD_ID: String = "conduit"

    // The action bar shows a message for about three seconds, so the status comes every two.
    private const val STATUS_INTERVAL_TICKS = 40

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
        config?.let(Triggers::apply)
        EditJournal.maxBlocks = { gameConfig.maxUndoBlocks }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ConduitCommand.register(dispatcher)
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % STATUS_INTERVAL_TICKS == 0) Bot.showStatus(server)
            Triggers.onTick(server)
        }

        // Singleplayer worlds start and stop an integrated server each time the player opens one.
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            GameServer.current = server
            config?.let(::startMcp)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { Bot.stopAll() }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            McpHost.stop()
            EditJournal.clear()
            if (GameServer.current === server) GameServer.current = null
        }
    }

    /**
     * Reads the config file again. Returns its problems, and keeps the old config when it has any.
     */
    fun reloadConfig(): List<String> {
        val updated =
            try {
                ConfigFile.read(toolActions())
            } catch (exception: InvalidConfig) {
                return exception.problems
            }

        config = updated
        Triggers.apply(updated)
        if (GameServer.current != null) startMcp(updated)
        return emptyList()
    }

    private fun toolActions(): Map<String, Set<String>> = ALL_TOOLS.associate {
        it.name to it.actions.keys
    }

    private fun readConfig(): ConduitConfig? =
        try {
            ConfigFile.read(toolActions())
        } catch (exception: InvalidConfig) {
            LOGGER.error(
                "Invalid {}, Conduit stays off until /conduit reload loads a fixed one: {}",
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
