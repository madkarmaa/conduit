package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
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

fun toolResult(reply: Reply): CallToolResult {
    val image = reply.image?.let { ImageContent(data = encodePng(it), mimeType = "image/png") }
    return CallToolResult(
        content = listOfNotNull(TextContent(reply.text), image),
        structuredContent = reply.data,
    )
}

private fun encodePng(pixels: Pixels): String {
    val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, pixels.width, pixels.height, pixels.argb, 0, pixels.width)

    val bytes = ByteArrayOutputStream()
    ImageIO.write(image, "png", bytes)
    return Base64.getEncoder().encodeToString(bytes.toByteArray())
}

fun errorResult(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)
