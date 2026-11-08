package top.madkarma.conduit

import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import top.madkarma.conduit.game.ConduitError

/** Reads a JSON object, so tests can write their arguments and expected data as JSON text. */
fun parseJson(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** The message of the invalid input error that the block throws, which callers see. */
fun invalidInputMessage(block: () -> Any?): String =
    assertFailsWith<ConduitError.InvalidInput> { block() }.message.orEmpty()
