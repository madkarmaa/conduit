package top.madkarma.conduit.mcp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import top.madkarma.conduit.config.TokenConfig

/** Finds the policy of a bearer token. */
object AccessTokens {
    @Volatile private var configured: List<TokenConfig> = emptyList()

    // Tokens the bot hands to its agents, valid while their request runs.
    private val issued = ConcurrentHashMap<String, ToolPolicy>()
    private val random = SecureRandom()

    fun issue(policy: ToolPolicy): String {
        val token = HexFormat.of().formatHex(ByteArray(32).also(random::nextBytes))
        issued[token] = policy
        return token
    }

    fun revoke(token: String) {
        issued.remove(token)
    }

    /** Sets the tokens of MCP hosts outside the game, which never wait for approvals. */
    fun configure(tokens: List<TokenConfig>) {
        configured = tokens
    }

    fun policyFor(token: String): ToolPolicy? {
        issued[token]?.let {
            return it
        }

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
