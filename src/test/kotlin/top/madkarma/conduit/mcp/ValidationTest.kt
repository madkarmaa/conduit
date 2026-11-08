package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import top.madkarma.conduit.invalidInputMessage
import top.madkarma.conduit.parseJson

class ValidationTest {
    private val schema = input {
        required("from", BLOCK_POS)
        optional("limit", integerSchema(min = 1, max = 10, default = 5))
        optional("mode", enumSchema(listOf("fast", "slow")))
        optional("items", arraySchema(ITEM))
    }
        .toJson()

    private fun invalidMessage(arguments: String): String = invalidInputMessage {
        validateArguments(schema, parseJson(arguments))
    }

    @Test
    fun `fills in the defaults of missing fields`() {
        val arguments =
            validateArguments(schema, parseJson("""{"from": {"x": 1, "y": 2, "z": 3}}"""))

        assertEquals(parseJson("""{"from": {"x": 1, "y": 2, "z": 3}, "limit": 5}"""), arguments)
    }

    @Test
    fun `treats a null like a missing field`() {
        val arguments =
            validateArguments(
                schema,
                parseJson("""{"from": {"x": 1, "y": 2, "z": 3}, "limit": null, "mode": null}"""),
            )

        assertEquals(parseJson("""{"from": {"x": 1, "y": 2, "z": 3}, "limit": 5}"""), arguments)
    }

    @Test
    fun `fills in defaults inside arrays of objects`() {
        val arguments =
            validateArguments(
                schema,
                parseJson(
                    """{"from": {"x": 0, "y": 0, "z": 0}, "items": [{"id": "minecraft:stone"}]}""",
                ),
            )

        val expected = parseJson("""{"items": [{"id": "minecraft:stone", "count": 1}]}""")
        assertEquals(expected["items"], arguments["items"])
    }

    @Test
    fun `accepts a whole number with a fraction as an integer`() {
        val arguments =
            validateArguments(schema, parseJson("""{"from": {"x": 3.0, "y": 2, "z": 1}}"""))

        assertEquals(parseJson("""{"x": 3.0, "y": 2, "z": 1}"""), arguments.getValue("from"))
    }

    @Test
    fun `names the field of each problem`() {
        val message = invalidMessage("""{"from": {"x": 1.5, "y": 2, "z": 3}, "limit": 11}""")

        assertEquals(
            "from.x: number found, integer expected; limit: must have a maximum value of 10",
            message,
        )
    }

    @Test
    fun `reports a missing required field without a location`() {
        assertEquals("required property 'from' not found", invalidMessage("{}"))
    }

    @Test
    fun `rejects a value outside the enum`() {
        val message = invalidMessage("""{"from": {"x": 0, "y": 0, "z": 0}, "mode": "medium"}""")

        assertEquals("mode: does not have a value in the enumeration [\"fast\", \"slow\"]", message)
    }
}
