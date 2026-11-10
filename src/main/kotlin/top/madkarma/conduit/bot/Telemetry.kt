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
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.LOGGER
import top.madkarma.conduit.config.AgentConfig
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.mcp.RecordedCall

private const val SHOWN_LENGTH = 2000
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
private const val HEADER_PREFIX = "### "

/**
 * Writes each request, the agent's tool calls and the answer to a text file while the telemetry
 * config is on, so operators can see how the agent works. A header names the mod version and the
 * agents once, and again where they change.
 */
object Telemetry {
    /** Relative to the server folder. */
    const val FILE = "logs/conduit-telemetry.txt"

    private val path: Path = FabricLoader.getInstance().gameDir.resolve(FILE)

    // The last header in the file. It is read from the file once, so a restart with the same setup
    // writes no new one.
    private var lastHeader: String? = null
    private var headerKnown = false

    /** A new trace of the request, or null while telemetry is off. */
    fun start(request: BotRequest, config: ConduitConfig): RequestTrace? =
        if (config.telemetry) RequestTrace(request, telemetryHeader(Conduit.version, config))
        else null

    /**
     * Writes the header first when the file is new or empty, or when the mod version or an agent
     * differs from the file's last header.
     */
    @Synchronized
    fun append(text: String, header: String) {
        try {
            Files.createDirectories(path.parent)
            val empty = !Files.exists(path) || Files.size(path) == 0L
            if (!headerKnown) {
                lastHeader = if (empty) null else lastHeaderIn(Files.readString(path))
                headerKnown = true
            }

            val start = if (empty || header != lastHeader) header else ""
            Files.writeString(path, start + text, CREATE, APPEND)
            lastHeader = header
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

/** The last header of a telemetry file, or null when it has none. */
internal fun lastHeaderIn(text: String): String? {
    val lines = text.lines()
    val end = lines.indexOfLast { it.startsWith(HEADER_PREFIX) }
    if (end < 0) return null

    var start = end
    while (start > 0 && lines[start - 1].startsWith(HEADER_PREFIX)) start--
    return lines.subList(start, end + 1).joinToString("\n", postfix = "\n\n")
}

/** The header of the file: the mod version and each mode's agent, never their keys or secrets. */
internal fun telemetryHeader(version: String, config: ConduitConfig): String =
    listOf(
            "${HEADER_PREFIX}Conduit $version",
            "${HEADER_PREFIX}full: ${describeAgent(config.agent(Mode.FULL))}",
            "${HEADER_PREFIX}chat: ${describeAgent(config.agent(Mode.CHAT))}",
        )
        .joinToString("\n", postfix = "\n\n")

/**
 * The lines of one request. They go to the file together when the request ends, so the requests of
 * the two modes never mix. The file's header names the agents, so a slow model shows apart from
 * slow tools.
 */
class RequestTrace(request: BotRequest, private val header: String) {
    private val startedAt = System.currentTimeMillis()
    private var calls = 0
    private val lines =
        mutableListOf(
            "=== ${LocalDateTime.now().format(DATE_TIME)} ${request.asker.name} in " +
                "${request.mode.id} mode",
            "Request: ${shorten(request.trigger?.let(::triggerPrompt) ?: request.text)}",
        )

    /** Each call shows when it started and how long it ran, so the gaps show the model's time. */
    @Synchronized
    fun record(call: RecordedCall) {
        calls++
        val start = LocalDateTime.now().minus(Duration.ofMillis(call.milliseconds))
        lines +=
            "${start.format(TIME)} ${call.tool} (${describeTime(call)}) " +
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
        Telemetry.append(text(answer), header)
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
    val effort = agent.effort.ifBlank { "its default" } + " effort"
    return "${agent.cli} $model, $effort"
}
