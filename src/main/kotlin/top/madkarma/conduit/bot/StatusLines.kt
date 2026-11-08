package top.madkarma.conduit.bot

import java.io.IOException
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.RunnerConfig
import top.madkarma.conduit.config.agentsInUse

// The lines of /conduit status. They never show secrets.

internal suspend fun describeMode(mode: Mode, config: ConduitConfig): Component {
    val agent =
        config.agentsInUse()[mode.id]
            ?: return Component.literal("${mode.id}: off").withStyle(ChatFormatting.GRAY)

    val line = Bot.line(mode)
    val asking = line.running()?.let { "answering ${it.asker.name}" } ?: "idle"
    val web = if (agent.web) "web on" else "web off"
    val credentials =
        if (agent.dangerouslyUseSubscriptionCredentials) "subscription login" else "API key"

    return Component.literal(
        "${mode.id}: ${agent.cli} ${agent.model} (${agent.effort}), $web, $credentials. Runner: " +
            "${runnerState(agent.runner, agent.cli)}. Line: $asking, ${line.waiting().size} waiting.",
    )
}

/** Each trigger, what starts it, and when it last ran. */
internal fun describeTriggers(config: ConduitConfig): Component? {
    if (config.triggers.isEmpty()) return null

    val lastStarts = Triggers.lastStarts()
    val now = System.currentTimeMillis()
    val triggers =
        config.triggers.joinToString(", ") { trigger ->
            val cause =
                if (trigger.everyMinutes > 0) "every ${trigger.everyMinutes} min"
                else trigger.events.joinToString("/")
            val last =
                lastStarts[trigger.name]?.let { "ran ${(now - it) / 60_000} min ago" }
                    ?: "not run yet"
            val state = if (trigger.enabled) last else "off"
            "${trigger.name} ($cause, $state)"
        }
    return Component.literal("triggers: $triggers")
}

private suspend fun runnerState(runner: RunnerConfig, cli: String): String =
    try {
        val health = RunnerClient(runner).health()
        val version = health.versions[cli] ?: "$cli not installed yet"
        val activity = if (health.updating) "updating" else if (health.busy) "busy" else "idle"
        "$version, $activity"
    } catch (_: RunnerRefused) {
        "refused the secret"
    } catch (_: IOException) {
        "unreachable"
    }
