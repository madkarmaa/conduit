package top.madkarma.conduit.config

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
