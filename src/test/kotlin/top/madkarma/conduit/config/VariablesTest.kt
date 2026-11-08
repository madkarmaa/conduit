package top.madkarma.conduit.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import top.madkarma.conduit.parseJson

class VariablesTest {
    private val environment = mapOf("HOST" to "minecraft", "PORT" to "25585")

    private fun expanded(
        text: String,
        missing: MutableList<MissingVariable> = mutableListOf(),
    ): String =
        (expandVariables(JsonPrimitive(text), environment, missing) as JsonPrimitive).content

    @Test
    fun `replaces every reference in a string`() {
        assertEquals("http://minecraft:25585/mcp", expanded($$"http://${HOST}:${PORT}/mcp"))
    }

    @Test
    fun `uses the default when the variable is not set`() {
        assertEquals("fallback", expanded($$"${UNSET:-fallback}"))
        assertEquals("minecraft", expanded($$"${HOST:-fallback}"))
        assertEquals("", expanded($$"${UNSET:-}"))
    }

    @Test
    fun `leaves text without braces alone`() {
        assertEquals($$"$HOST and ${not a name}", expanded($$"$HOST and ${not a name}"))
    }

    @Test
    fun `a missing variable becomes empty and names the field that uses it`() {
        val missing = mutableListOf<MissingVariable>()
        val config =
            parseJson(
                $$"""{"agents": {"full": {"runner": {"secret": "${SECRET}"}}}, "mcp": {"tokens": [{"token": "x${TOKEN}"}]}}""",
            )

        val result = expandVariables(config, environment, missing)

        assertEquals(
            parseJson(
                """{"agents": {"full": {"runner": {"secret": ""}}}, "mcp": {"tokens": [{"token": "x"}]}}""",
            ),
            result,
        )
        assertEquals(
            listOf("SECRET" to "agents.full.runner.secret", "TOKEN" to "mcp.tokens[0].token"),
            missing.map { it.name to it.path },
        )
    }

    @Test
    fun `leaves values that are not strings alone`() {
        val config = parseJson("""{"port": 25585, "enabled": true, "name": null}""")

        assertEquals(config, expandVariables(config, environment, mutableListOf()))
    }

    @Test
    fun `the defaults read the API keys and runner secrets from the environment`() {
        val json = Json { encodeDefaults = true }
        val defaults: JsonElement = json.encodeToJsonElement(ConduitConfig())
        val missing = mutableListOf<MissingVariable>()
        val secrets =
            mapOf(
                "CONDUIT_FULL_API_KEY" to "full-key",
                "CONDUIT_FULL_SECRET" to "full-secret-0123456789",
                "CONDUIT_CHAT_API_KEY" to "chat-key",
                "CONDUIT_CHAT_SECRET" to "chat-secret-0123456789",
            )

        expandVariables(defaults, emptyMap(), missing)
        val config =
            json.decodeFromJsonElement<ConduitConfig>(
                expandVariables(defaults, secrets, mutableListOf()),
            )

        assertEquals(
            listOf(
                "CONDUIT_FULL_API_KEY" to "agents.full.apiKey",
                "CONDUIT_FULL_SECRET" to "agents.full.runner.secret",
                "CONDUIT_CHAT_API_KEY" to "agents.chat.apiKey",
                "CONDUIT_CHAT_SECRET" to "agents.chat.runner.secret",
            ),
            missing.map { it.name to it.path },
        )
        assertEquals("full-key", config.agents.full.apiKey)
        assertEquals(emptyList(), config.problems(TOOL_ACTIONS))
    }
}
