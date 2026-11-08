package top.madkarma.conduit.bot

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.fabricmc.loader.api.FabricLoader
import top.madkarma.conduit.LOGGER
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
        if (config.telemetry) RequestTrace(request) else null

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
 * the two modes never mix.
 */
class RequestTrace(request: BotRequest) {
    private val startedAt = System.currentTimeMillis()
    private val lines =
        mutableListOf(
            "=== ${LocalDateTime.now().format(DATE_TIME)} ${request.asker.name} in " +
                "${request.mode.id} mode",
            "Request: ${shorten(request.trigger?.config?.prompt ?: request.text)}",
        )

    @Synchronized
    fun record(call: RecordedCall) {
        lines +=
            "${LocalDateTime.now().format(TIME)} ${call.tool} ${shorten(call.arguments.toString())}"
        lines += "  ${if (call.failed) "failed" else "ok"}: ${shorten(call.text)}"
    }

    @Synchronized
    fun finish(answer: String) {
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        lines += "Answer after $seconds s: ${shorten(answer)}"
        Telemetry.append(lines.joinToString("\n", postfix = "\n\n"))
    }

    // Results can be long, such as a region's blocks, and their own lines would break the layout.
    private fun shorten(text: String): String {
        val shown = if (text.length > SHOWN_LENGTH) text.take(SHOWN_LENGTH) + "…" else text
        return shown.replace("\n", "\n    ")
    }
}
