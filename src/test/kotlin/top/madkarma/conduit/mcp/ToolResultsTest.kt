package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.parseJson

class ToolResultsTest {
    @Test
    fun `a result holds the text and the data`() {
        val data = parseJson("""{"count": 2}""")

        val result = toolResult(Reply("2 blocks", data))

        assertEquals(listOf("2 blocks"), result.content.map { (it as TextContent).text })
        assertEquals(data, result.structuredContent)
    }

    @Test
    fun `an image becomes a PNG with the same pixels`() {
        val red = 0xFFFF0000.toInt()
        val clear = 0x00000000
        val result = toolResult(Reply("Top view", image = Pixels(2, 1, intArrayOf(red, clear))))

        val image = result.content[1] as ImageContent
        assertEquals("image/png", image.mimeType)

        val decoded = ImageIO.read(Base64.getDecoder().decode(image.data).inputStream())
        assertEquals(2, decoded.width)
        assertEquals(1, decoded.height)
        assertEquals(red, decoded.getRGB(0, 0))
        assertEquals(clear, decoded.getRGB(1, 0))
    }

    @Test
    fun `an error result is marked as an error`() {
        val result = errorResult("Not found: nothing")

        assertEquals(true, result.isError)
        assertEquals("Not found: nothing", (result.content.single() as TextContent).text)
    }

    @Test
    fun `failures name their kind`() {
        assertEquals(
            "Not found: Unknown dimension: minecraft:moon",
            describeFailure(
                ConduitError.NotFound("Unknown dimension: minecraft:moon"),
                "world_info",
            ),
        )
        assertEquals(
            "Invalid input: bad id",
            describeFailure(IllegalArgumentException("bad id"), "find"),
        )
        assertEquals(
            "Failed precondition: not loaded",
            describeFailure(IllegalStateException("not loaded"), "find"),
        )
    }

    @Test
    fun `unexpected failures are internal errors`() {
        assertEquals(
            "Internal error: java.lang.UnsupportedOperationException: boom",
            describeFailure(UnsupportedOperationException("boom"), "find"),
        )
    }
}
