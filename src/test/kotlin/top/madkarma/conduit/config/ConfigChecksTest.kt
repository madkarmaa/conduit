package top.madkarma.conduit.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigChecksTest {
    private fun problemsOf(config: ConduitConfig): List<String> = config.problems(TOOL_ACTIONS)

    @Test
    fun `the defaults with their agents filled in have no problems`() {
        assertEquals(emptyList(), problemsOf(VALID_CONFIG))
    }

    @Test
    fun `checks the numbers of every section`() {
        val config =
            VALID_CONFIG.copy(
                mcp = McpConfig(port = 70000, allowedHosts = emptyList()),
                approvals = ApprovalsConfig(timeoutSeconds = 0),
                requests = RequestsConfig(timeoutSeconds = -1),
                replies = RepliesConfig(maxLength = 19),
                game = GameConfig(maxStructureVolume = 0, maxUndoBlocks = -1),
            )

        val expected =
            listOf(
                "mcp.port must be between 1 and 65535",
                "mcp.allowedHosts must name at least one host",
                "approvals.timeoutSeconds must be positive",
                "requests.timeoutSeconds must be positive",
                "replies.maxLength must be at least 20",
                "game.maxStructureVolume must be positive",
                "game.maxUndoBlocks must not be negative",
            )
        assertEquals(expected, problemsOf(config))
    }

    @Test
    fun `undo may be turned off`() {
        assertEquals(
            emptyList(),
            problemsOf(VALID_CONFIG.copy(game = GameConfig(maxUndoBlocks = 0))),
        )
    }

    @Test
    fun `checks the MCP tokens`() {
        val tokens =
            listOf(
                TokenConfig(name = "", token = " "),
                TokenConfig(
                    name = "desktop",
                    token = "a",
                    allow = listOf("get_player", "teleport"),
                ),
                TokenConfig(name = "desktop", token = "a", deny = listOf("fly")),
            )

        val expected =
            listOf(
                "mcp.tokens[0].name must not be empty",
                "mcp.tokens[0].token must not be empty",
                "mcp.tokens[1] names unknown tool teleport",
                "mcp.tokens[2] names unknown tool fly",
                "mcp.tokens names repeat",
                "mcp.tokens values repeat",
            )
        assertEquals(expected, problemsOf(VALID_CONFIG.copy(mcp = McpConfig(tokens = tokens))))
    }

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
    fun `tool lists accept tools and their actions`() {
        val config =
            VALID_CONFIG.copy(
                tools =
                    ToolsConfig(
                        chat =
                            ChatToolsConfig(
                                allow = listOf("get_player"),
                                deny = listOf("get_player.stats"),
                            ),
                    ),
                approvals =
                    ApprovalsConfig(
                        add = listOf("server_admin.save"),
                        remove = listOf("server_admin"),
                    ),
            )

        assertEquals(emptyList(), problemsOf(config))
    }

    @Test
    fun `tool lists refuse unknown tools and actions`() {
        val config =
            VALID_CONFIG.copy(
                tools =
                    ToolsConfig(
                        chat =
                            ChatToolsConfig(allow = listOf("fly"), deny = listOf("get_player.fly")),
                    ),
                approvals =
                    ApprovalsConfig(add = listOf("server_admin."), remove = listOf("nope.stop")),
            )

        val expected =
            listOf(
                "tools.chat names unknown tool or action fly",
                "tools.chat names unknown tool or action get_player.fly",
                "approvals names unknown tool or action server_admin.",
                "approvals names unknown tool or action nope.stop",
            )
        assertEquals(expected, problemsOf(config))
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
