package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.Vector3
import top.madkarma.conduit.parseJson

class FormatTest {
    @Serializable private data class Sample(val name: String?, val tags: List<String>)

    private fun compacted(text: String): String = compact(parseJson(text)).toString()

    @Test
    fun `compact drops nulls and empty values at every level`() {
        val text =
            """
            {
                "missing": null, "blank": "", "none": [], "nothing": {},
                "nested": {"inner": null}, "mixed": [null, "", 1, {}],
                "off": false, "zero": 0, "name": "x"
            }
            """

        assertEquals("""{"mixed":[1],"off":false,"zero":0,"name":"x"}""", compacted(text))
    }

    @Test
    fun `compact writes whole numbers without a fraction`() {
        assertEquals(
            """{"a":3,"b":-2,"c":3.5,"d":12}""",
            compacted("""{"a": 3.0, "b": -2.0, "c": 3.5, "d": 12}"""),
        )
    }

    @Test
    fun `compact keeps huge numbers as they are`() {
        assertEquals("""{"big":1e20}""", compacted("""{"big": 1e20}"""))
    }

    @Test
    fun `compact returns null when nothing is left`() {
        assertNull(compact(parseJson("""{"a": {"b": [null, ""]}}""")))
        assertNull(compact(JsonArray(emptyList())))
        assertNull(compact(JsonNull))
    }

    @Test
    fun `json replies with compact text and the same data`() {
        val reply = json(BlockCoordinates(1, 0, -3))

        assertEquals("""{"x":1,"y":0,"z":-3}""", reply.text)
        assertEquals(parseJson(reply.text), reply.data)
    }

    @Test
    fun `json of an object without values replies with an empty object`() {
        val reply = json(Sample(null, emptyList()))

        assertEquals("{}", reply.text)
        assertEquals(JsonObject(emptyMap()), reply.data)
    }

    @Test
    fun `round keeps two decimals and drops a zero fraction`() {
        assertEquals("12.5", round(12.5))
        assertEquals("64", round(64.0))
        assertEquals("1.23", round(1.234))
        assertEquals("-3.26", round(-3.257))
        assertEquals("0", round(-0.001))
    }

    @Test
    fun `position joins rounded coordinates`() {
        assertEquals("1 64.5 -3.26", position(Vector3(1.0, 64.5, -3.257)))
        assertEquals("?", position(null))
    }

    @Test
    fun `noteUndo only adds a note when undo cannot revert the change`() {
        val data = parseJson("""{"changed": 3}""")
        val reply = Reply("Filled 3 blocks", data)

        assertSame(reply, reply.noteUndo(true))
        val noted = reply.noteUndo(false)
        assertEquals("Filled 3 blocks\nThis change is too large to undo.", noted.text)
        assertEquals(data, noted.data)
    }

    @Test
    fun `lines puts the header above the rows`() {
        val data = dataOf("count" to JsonPrimitive(2))
        val reply = lines("2 results", listOf("a", "b"), data)

        assertEquals("2 results\na\nb", reply.text)
        assertEquals(data, reply.data)
    }
}
