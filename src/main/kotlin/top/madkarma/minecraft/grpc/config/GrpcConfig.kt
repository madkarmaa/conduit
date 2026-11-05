package top.madkarma.minecraft.grpc.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*

data class GrpcConfig(
    var enabled: Boolean = true,
    var host: String = "127.0.0.1",
    var port: Int = 25585,
    var requireAuth: Boolean = true,
    var token: String = "",
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraft_grpc")
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val PATH: Path = FabricLoader.getInstance().configDir.resolve("minecraft-grpc.json")

        fun load(): GrpcConfig {
            val config = readFile() ?: GrpcConfig()
            if (config.token.isBlank()) {
                config.token = generateToken()
            }

            write(config)
            applyEnvironment(config)
            return config
        }

        private fun readFile(): GrpcConfig? {
            if (!Files.exists(PATH)) return null

            return try {
                Files.newBufferedReader(PATH).use { GSON.fromJson(it, GrpcConfig::class.java) }
            } catch (exception: JsonParseException) {
                LOGGER.error("Invalid {}, using defaults", PATH, exception)
                null
            }
        }

        private fun write(config: GrpcConfig) {
            Files.createDirectories(PATH.parent)
            Files.newBufferedWriter(PATH).use { GSON.toJson(config, it) }
        }

        private fun applyEnvironment(config: GrpcConfig) {
            env("ENABLED")?.let { config.enabled = it.toBoolean() }
            env("HOST")?.let { config.host = it }
            env("PORT")?.toIntOrNull()?.let { config.port = it }
            env("TOKEN")?.let { config.token = it }
            env("REQUIRE_AUTH")?.let { config.requireAuth = it.toBoolean() }
        }

        private fun env(name: String): String? =
            System.getenv("MINECRAFT_GRPC_$name")?.takeIf { it.isNotBlank() }

        private fun generateToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return HexFormat.of().formatHex(bytes)
        }
    }
}
