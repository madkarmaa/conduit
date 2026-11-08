package top.madkarma.conduit.config

/** The tool and action names that config checks accept in tests. */
val TOOL_ACTIONS: Map<String, Set<String>> =
    mapOf("get_player" to setOf("info", "stats"), "server_admin" to setOf("stop", "save"))

private fun filledIn(agent: AgentConfig): AgentConfig =
    agent.copy(
        cli = "claude",
        model = "sonnet",
        effort = "medium",
        apiKey = "provider-key",
        runner = agent.runner.copy(secret = "0123456789abcdef"),
    )

/**
 * The default config with its CLIs, models, efforts, API keys and runner secrets set, which has no
 * problems.
 */
val VALID_CONFIG: ConduitConfig =
    ConduitConfig().let {
        it.copy(agents = AgentsConfig(filledIn(it.agents.full), filledIn(it.agents.chat)))
    }

fun ConduitConfig.withFullAgent(change: AgentConfig.() -> AgentConfig): ConduitConfig =
    copy(agents = agents.copy(full = agents.full.change()))
