package top.madkarma.conduit.config

import top.madkarma.conduit.events.EventType

private val EVENT_IDS = EventType.entries.map { it.id }.toSet()

/** Returns what is wrong with the triggers. */
fun ConduitConfig.triggerProblems(): List<String> {
    val problems = mutableListOf<String>()

    triggers.forEachIndexed { index, trigger ->
        val path = "triggers[$index]"
        val timed = trigger.everyMinutes > 0

        if (trigger.name.isBlank()) problems += "$path.name must not be empty"
        if (trigger.prompt.isBlank()) problems += "$path.prompt must not be empty"
        if (trigger.cooldownSeconds < 0) problems += "$path.cooldownSeconds must not be negative"
        if (trigger.everyMinutes < 0) problems += "$path.everyMinutes must not be negative"
        if (timed == trigger.events.isNotEmpty())
            problems += "$path needs either events or everyMinutes"
        problems +=
            trigger.events.filter { it !in EVENT_IDS }.map { "$path names unknown event $it" }
    }

    if (triggers.distinctBy { it.name }.size != triggers.size) problems += "triggers names repeat"
    if (triggers.any { it.enabled } && !agents.full.enabled) {
        problems += "triggers run in full mode, so agents.full must be enabled"
    }
    return problems
}
