package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.slf4j.LoggerFactory
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.game.asConduitError

private val LOGGER = LoggerFactory.getLogger("conduit")

/** The text a caller sees for a failed call. Unexpected failures also go to the log. */
fun describeFailure(exception: Exception, tool: String): String {
    val error = exception.asConduitError()
    if (error is ConduitError) return error.describe()

    LOGGER.error("Unexpected error in tool {}", tool, error)
    return "Internal error: $error"
}

fun toolResult(reply: Reply): CallToolResult =
    CallToolResult(content = listOf(TextContent(reply.text)), structuredContent = reply.data)

fun errorResult(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)
