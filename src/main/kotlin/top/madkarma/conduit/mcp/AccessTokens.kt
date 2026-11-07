package top.madkarma.conduit.mcp

import java.security.MessageDigest
import top.madkarma.conduit.config.TokenConfig

/** Finds the policy of a bearer token. */
object AccessTokens {
    @Volatile private var configured: List<TokenConfig> = emptyList()

    /** Sets the tokens of MCP hosts outside the game, which never wait for approvals. */
    fun configure(tokens: List<TokenConfig>) {
        configured = tokens
    }

    fun policyFor(token: String): ToolPolicy? {
        val provided = token.toByteArray()
        // Comparing in constant time keeps response times from revealing a token.
        val match =
            configured.firstOrNull { MessageDigest.isEqual(it.token.toByteArray(), provided) }
                ?: return null

        return ToolPolicy(
            name = "token ${match.name}",
            readOnly = match.readOnly,
            allowedTools = match.allow.toSet().ifEmpty { null },
            deniedTools = match.deny.toSet(),
        )
    }
}
