package top.madkarma.conduit.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentChecksTest {
    private fun problemsOf(config: ConduitConfig): List<String> = config.problems(TOOL_ACTIONS)

    @Test
    fun `checks every field of an enabled agent`() {
        val config = VALID_CONFIG.withFullAgent {
            copy(
                cli = "gemini",
                model = " ",
                effort = "",
                runner = RunnerConfig("http://conduit_full:8080", "short"),
                mcpUrl = "ftp://minecraft/mcp",
                apiKey = "",
            )
        }

        val expected =
            listOf(
                "agents.full.cli must be claude, codex or opencode. For another CLI, please open an issue.",
                "agents.full.model must be set to the model, as the CLI names it",
                "agents.full.effort must be set to the reasoning effort, as the CLI names it",
                "agents.full.runner.url must be an http or https URL with a host name without underscores",
                "agents.full.runner.secret must have at least 16 letters, digits or -._~+/ characters",
                "agents.full.mcpUrl must be an http or https URL",
                "agents.full.apiKey must be set, or agents.full.dangerouslyUseSubscriptionCredentials turned on",
            )
        assertEquals(expected, problemsOf(config))
    }

    @Test
    fun `the CLI, model and effort have no defaults`() {
        val agents = ConduitConfig().agents
        val config =
            VALID_CONFIG.copy(
                agents =
                    VALID_CONFIG.agents.copy(
                        chat =
                            agents.chat.copy(
                                apiKey = "provider-key",
                                runner = agents.chat.runner.copy(secret = "0123456789abcdef"),
                            ),
                    ),
            )

        val expected =
            listOf(
                "agents.chat.cli must be set to claude, codex or opencode",
                "agents.chat.model must be set to the model, as the CLI names it",
                "agents.chat.effort must be set to the reasoning effort, as the CLI names it",
            )
        assertEquals(expected, problemsOf(config))
    }

    @Test
    fun `a disabled agent needs no CLI, model or effort`() {
        val config = VALID_CONFIG.withFullAgent {
            copy(enabled = false, cli = "", model = "", effort = "")
        }

        assertEquals(emptyList(), problemsOf(config))
    }

    @Test
    fun `a disabled agent is not checked`() {
        val config = VALID_CONFIG.withFullAgent {
            copy(enabled = false, cli = "gemini", apiKey = "")
        }

        assertEquals(emptyList(), problemsOf(config))
    }

    @Test
    fun `a subscription login stands in for the API key`() {
        val config = VALID_CONFIG.withFullAgent {
            copy(apiKey = "", dangerouslyUseSubscriptionCredentials = true)
        }

        assertEquals(emptyList(), problemsOf(config))
    }

    @Test
    fun `the MCP URL may use a host name with underscores`() {
        val config = VALID_CONFIG.withFullAgent { copy(mcpUrl = "http://my_server:25585/mcp") }

        assertEquals(emptyList(), problemsOf(config))
    }

    @Test
    fun `a runner secret may end with padding`() {
        val config = VALID_CONFIG.withFullAgent {
            copy(runner = runner.copy(secret = "abcdefghijklmnop=="))
        }

        assertEquals(emptyList(), problemsOf(config))
    }

    @Test
    fun `a disabled agent's fields are not in use`() {
        val config = VALID_CONFIG.withFullAgent { copy(enabled = false) }

        assertFalse(config.uses("agents.full.runner.secret"))
        assertTrue(config.uses("agents.chat.runner.secret"))
        assertTrue(config.uses("mcp.tokens[0].token"))
    }

    @Test
    fun `the API key is not in use with a subscription login`() {
        val config = VALID_CONFIG.withFullAgent {
            copy(dangerouslyUseSubscriptionCredentials = true)
        }

        assertFalse(config.uses("agents.full.apiKey"))
        assertTrue(config.uses("agents.full.runner.secret"))
        assertTrue(config.uses("agents.chat.apiKey"))
    }
}
