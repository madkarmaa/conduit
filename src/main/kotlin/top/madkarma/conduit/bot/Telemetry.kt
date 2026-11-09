package top.madkarma.conduit.bot

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.fabricmc.loader.api.FabricLoader
import top.madkarma.conduit.LOGGER
import top.madkarma.conduit.config.AgentConfig
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.mcp.RecordedCall

private const val SHOWN_LENGTH = 2000
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Writes each request, the agent's tool calls and the answer to a text file while the telemetry
 * config is on, so operators can see how the agent works.
 */
object Telemetry {
    /** Relative to the server folder. */
    const val FILE = "logs/conduit-telemetry.txt"

    private val path: Path = FabricLoader.getInstance().gameDir.resolve(FILE)

    /** A new trace of the request, or null while telemetry is off. */
    fun start(request: BotRequest, config: ConduitConfig): RequestTrace? =
        if (config.telemetry) RequestTrace(request, config.agent(request.mode)) else null

    @Synchronized
    fun append(text: String) {
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, text, CREATE, APPEND)
        } catch (exception: IOException) {
            LOGGER.warn("Cannot write the telemetry to {}: {}", FILE, exception.toString())
        }
    }

    /** Empties the file, and creates it when it is missing. */
    @Synchronized
    fun clear() {
        Files.createDirectories(path.parent)
        Files.writeString(path, "")
    }
}

/**
 * The lines of one request. They go to the file together when the request ends, so the requests of
 * the two modes never mix. The header names the agent, so a slow model shows apart from slow tools.
 */
class RequestTrace(request: BotRequest, agent: AgentConfig) {
    private val startedAt = System.currentTimeMillis()
    private var calls = 0
    private val lines =
        mutableListOf(
            "=== ${LocalDateTime.now().format(DATE_TIME)} ${request.asker.name} in " +
                "${request.mode.id} mode with ${describeAgent(agent)}",
            "Request: ${shorten(request.trigger?.let(::triggerPrompt) ?: request.text)}",
        )

    /** Each call shows when it started and how long it ran, so the gaps show the model's time. */
    @Synchronized
    fun record(call: RecordedCall) {
        calls++
        val start = LocalDateTime.now().minus(Duration.ofMillis(call.milliseconds))
        lines +=
            "${start.format(TIME)} ${call.tool} (${call.milliseconds} ms) " +
                shorten(call.arguments.toString())
        lines += "  ${if (call.failed) "failed" else "ok"}: ${shorten(call.text)}"
    }

    /** Notes what the run itself showed, such as an agent that failed or made no tool calls. */
    @Synchronized
    fun noteRun(result: RunResult) {
        val error =
            result.stderrTail.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        when {
            result.cancelled -> return
            result.exitCode != 0 ->
                lines +=
                    "Note: the agent exited with code ${result.exitCode}: ${shorten(error.orEmpty())}"
            calls == 0 -> lines += "Note: the agent made no tool calls"
        }
    }

    @Synchronized
    fun finish(answer: String) {
        Telemetry.append(text(answer))
    }

    @Synchronized
    fun text(answer: String): String {
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        val all = lines + "Answer after $seconds s: ${shorten(answer)}"
        return all.joinToString("\n", postfix = "\n\n")
    }

    // Results can be long, such as a region's blocks, and their own lines would break the layout.
    private fun shorten(text: String): String {
        val shown = if (text.length > SHOWN_LENGTH) text.take(SHOWN_LENGTH) + "…" else text
        return shown.replace("\n", "\n    ")
    }
}

// Only the CLI, model and effort, never the agent's keys or runner secret.
private fun describeAgent(agent: AgentConfig): String {
    val model = agent.model.ifBlank { "its default model" }
    val effort = agent.effort.takeIf { it.isNotBlank() }?.let { ", $it effort" }.orEmpty()
    return "${agent.cli} $model$effort"
}
