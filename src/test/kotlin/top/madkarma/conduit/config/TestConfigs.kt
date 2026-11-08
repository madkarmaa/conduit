package top.madkarma.conduit.config

/** The tool and action names that config checks accept in tests. */
val TOOL_ACTIONS: Map<String, Set<String>> =
    mapOf("get_player" to setOf("info", "stats"), "server_admin" to setOf("stop", "save"))

private fun withSecrets(agent: AgentConfig): AgentConfig =
    agent.copy(apiKey = "provider-key", runner = agent.runner.copy(secret = "0123456789abcdef"))

/** The default config with its API keys and runner secrets set, which has no problems. */
val VALID_CONFIG: ConduitConfig =
    ConduitConfig().let {
        it.copy(agents = AgentsConfig(withSecrets(it.agents.full), withSecrets(it.agents.chat)))
    }

fun ConduitConfig.withFullAgent(change: AgentConfig.() -> AgentConfig): ConduitConfig =
    copy(agents = agents.copy(full = agents.full.change()))
