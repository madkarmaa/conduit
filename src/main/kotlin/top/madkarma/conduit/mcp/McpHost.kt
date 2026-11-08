package top.madkarma.conduit.mcp

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.DnsRebindingProtection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.serialization.json.JsonObject
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import top.madkarma.conduit.LOGGER
import top.madkarma.conduit.config.McpConfig

private const val PATH = "/mcp"

private val INSTRUCTIONS =
    listOf(
            "Controls a running Minecraft server through the Conduit mod.",
            "Positions are block coordinates, and y grows upwards. Dimensions default to the overworld.",
            "Use the game_data tool to look up valid block, item and entity ids instead of guessing them.",
            "Prefer the dedicated tools. run_command covers anything they do not.",
        )
        .joinToString(" ")

/**
 * Serves the tools over MCP's streamable HTTP transport. Each request builds its own server from
 * the policy of its bearer token, so a token that expires stops working with its next request.
 */
object McpHost {
    private var engine: EmbeddedServer<*, *>? = null
    private var running: McpConfig? = null

    @Volatile private var tools: List<ToolDefinition> = emptyList()
    @Volatile private var version: String = "unknown"

    /** Starts the server, or restarts it when the address or the allowed hosts changed. */
    @Synchronized
    fun apply(config: McpConfig, tools: List<ToolDefinition>, version: String) {
        this.tools = tools
        this.version = version
        if (engine != null && running?.let { sameAddress(it, config) } == true) return

        stop()
        quietSdkLogs()
        engine =
            embeddedServer(CIO, host = config.host, port = config.port) { module(config) }
                .start(wait = false)
        running = config
        LOGGER.info("MCP server listening on {}:{}{}", config.host, config.port, PATH)
    }

    @Synchronized
    fun stop() {
        val current = engine ?: return
        engine = null
        running = null
        current.stop(1000, 3000)
        LOGGER.info("MCP server stopped")
    }

    private fun Application.module(config: McpConfig) {
        install(ContentNegotiation) { json(McpJson) }

        routing {
            route(PATH) {
                install(DnsRebindingProtection) { allowedHosts = config.allowedHosts }
                post { serve(call) }
                // Every response comes back as JSON, so there is no event stream to open and no
                // session to end.
                get { refuse(call) }
                delete { refuse(call) }
            }
        }
    }

    private suspend fun serve(call: RoutingCall) {
        val token = call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
        val policy = token?.let { AccessTokens.policyFor(it) }

        if (policy == null) {
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            call.respondText(
                "Missing or invalid bearer token",
                status = HttpStatusCode.Unauthorized,
            )
            return
        }

        val transport =
            StreamableHttpServerTransport(
                StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
            )
        transport.setSessionIdGenerator(null)

        val session = createServer(ToolSession(policy, tools)).createSession(transport)
        try {
            transport.handleRequest(null, call)
        } finally {
            session.close()
        }
    }

    private fun createServer(session: ToolSession): Server {
        val capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
        val server =
            Server(
                Implementation("conduit", version),
                ServerOptions(capabilities),
                instructions(session.policy),
            )

        for (tool in session.offered.values) {
            server.addTool(tool.listing()) { request ->
                session.call(tool.name, request.arguments ?: JsonObject(emptyMap()))
            }
        }

        return server
    }

    private suspend fun refuse(call: RoutingCall) {
        call.response.header(HttpHeaders.Allow, HttpMethod.Post.value)
        call.respondText("Only POST is supported", status = HttpStatusCode.MethodNotAllowed)
    }

    private fun instructions(policy: ToolPolicy): String {
        val player = policy.player ?: return INSTRUCTIONS
        return "$INSTRUCTIONS The tools only accept and return data about the player ${player.name}."
    }

    private fun sameAddress(first: McpConfig, second: McpConfig): Boolean =
        first.host == second.host &&
            first.port == second.port &&
            first.allowedHosts == second.allowedHosts

    // The SDK logs every tool it registers, and the server registers them for each request. Its
    // feature registries log under names of their own, outside its package.
    private fun quietSdkLogs() {
        System.setProperty("kotlin-logging.logStartupMessage", "false")

        val registries =
            listOf("Tool", "Prompt", "Resource", "ResourceTemplate").map { "FeatureRegistry[$it]" }

        val names = listOf("io.modelcontextprotocol", "io.ktor") + registries
        Configurator.setLevel(names.associateWith { Level.WARN })
    }
}
