package top.madkarma.conduit.bot

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.madkarma.conduit.config.RunnerConfig

private val HTTP: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

private val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// Only the runner reads these, from the JSON. Data classes would print the secrets in toString.
@Suppress("unused") @Serializable class McpAccess(val url: String, val token: String)

@Suppress("unused")
@Serializable
class RunRequest(
    val cli: String,
    val model: String,
    val effort: String,
    val options: JsonObject,
    val web: Boolean,
    /** The system prompt. */
    val instructions: String,
    val prompt: String,
    /** The conversation's folder in the container. */
    val conversation: String,
    /** Continues the conversation if it has started, and keeps its folder. */
    val mcp: McpAccess,
    /** Empty when the CLI uses its subscription login instead. */
    val apiKey: String,
    val dangerouslyUseSubscriptionCredentials: Boolean,
    val timeoutSeconds: Int,
)

@Serializable
class RunResult(
    val exitCode: Int? = null,
    val output: String = "",
    val stderrTail: String = "",
    val cancelled: Boolean = false,
    val timedOut: Boolean = false,
)

@Serializable
class RunnerHealth(
    val versions: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val updating: Boolean = false,
)

/** The runner answered, but refused the call, such as while it installs the CLI. */
class RunnerRefused(val status: Int, message: String) : Exception(message)

/** Talks to the runner in an agent container. The secret never leaves this class. */
class RunnerClient(private val runner: RunnerConfig) {
    suspend fun run(request: RunRequest): RunResult {
        val body = JSON.encodeToString(request)
        // The deadline of the request ends runs long before this, by cancelling them.
        return send("POST", "/run", body, Duration.ofSeconds(request.timeoutSeconds + 60L))
    }

    suspend fun cancel() {
        send<Map<String, Boolean>>("POST", "/cancel", "{}", Duration.ofSeconds(10))
    }

    suspend fun health(): RunnerHealth = send("GET", "/health", null, Duration.ofSeconds(10))

    private suspend inline fun <reified T> send(
        method: String,
        path: String,
        body: String?,
        timeout: Duration,
    ): T {
        val publisher =
            body?.let { HttpRequest.BodyPublishers.ofString(it) }
                ?: HttpRequest.BodyPublishers.noBody()

        val request =
            HttpRequest.newBuilder(URI(runner.url.trimEnd('/') + path))
                .timeout(timeout)
                .header("Authorization", "Bearer ${runner.secret}")
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build()

        val response = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() != 200)
            throw RunnerRefused(response.statusCode(), errorOf(response.body()))

        return try {
            JSON.decodeFromString(response.body())
        } catch (exception: SerializationException) {
            throw IOException("The runner sent an unexpected answer", exception)
        }
    }

    private fun errorOf(body: String): String =
        runCatching { JSON.decodeFromString<Map<String, String>>(body)["error"] }.getOrNull()
            ?: body.take(200)
}
