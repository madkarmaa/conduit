package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import top.madkarma.conduit.parseJson

class SchemaTest {
    @Test
    fun `input lists the required fields in the order they were added`() {
        val schema = input {
            required("name", stringSchema())
            optional("count", integerSchema())
            required("id", stringSchema())
        }

        val expected =
            parseJson(
                """
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "count": {"type": "integer"},
                        "id": {"type": "string"}
                    },
                    "required": ["name", "id"]
                }
                """,
            )
        assertEquals(expected, schema.toJson())
        assertEquals(listOf("name", "count", "id"), schema.properties.keys.toList())
    }

    @Test
    fun `input without required fields leaves out the required list`() {
        assertEquals(parseJson("""{"type": "object", "properties": {}}"""), input {}.toJson())
    }

    @Test
    fun `field schemas only write the limits and defaults they get`() {
        assertEquals(parseJson("""{"type": "number"}"""), numberSchema())
        assertEquals(
            parseJson(
                """{"type": "integer", "minimum": 1, "maximum": 5, "default": 2, "description": "Count."}""",
            ),
            integerSchema("Count.", min = 1, max = 5, default = 2),
        )
        assertEquals(
            parseJson("""{"type": "string", "default": "a", "maxLength": 3}"""),
            stringSchema(default = "a", maxLength = 3),
        )
        assertEquals(
            parseJson("""{"type": "boolean", "default": false}"""),
            booleanSchema(default = false),
        )
    }

    @Test
    fun `enum and array schemas keep their values in order`() {
        assertEquals(
            parseJson("""{"type": "string", "enum": ["b", "a"], "default": "a"}"""),
            enumSchema(listOf("b", "a"), default = "a"),
        )
        assertEquals(
            parseJson(
                """{"type": "array", "items": {"type": "string"}, "minItems": 1, "maxItems": 3, "default": []}""",
            ),
            arraySchema(stringSchema(), minItems = 1, maxItems = 3, default = EMPTY_ARRAY),
        )
    }

    @Test
    fun `described replaces the description and keeps the rest`() {
        val schema = stringSchema("Old.", default = "x").described("New.")

        assertEquals(
            parseJson("""{"type": "string", "default": "x", "description": "New."}"""),
            schema,
        )
    }

    @Test
    fun `object schemas nest an input with its description`() {
        val schema = objectSchema("A point.") { required("x", integerSchema()) }

        assertEquals(JsonPrimitive("A point."), schema["description"])
        assertEquals(JsonArray(listOf(JsonPrimitive("x"))), schema["required"])
        assertEquals(setOf("x"), schema.getValue("properties").jsonObject.keys)
    }
}
