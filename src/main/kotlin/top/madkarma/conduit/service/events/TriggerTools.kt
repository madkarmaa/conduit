package top.madkarma.conduit.service.events

import top.madkarma.conduit.config.TriggerConfig
import top.madkarma.conduit.mcp.*

private val EVENT_NAMES = FEED_TYPES.map { it.id }

val TRIGGERS =
    ToolDefinition(
        name = "triggers",
        title = "Triggers",
        description =
            "Triggers run the full mode agent on game events or every few minutes, with " +
                "the trigger's prompt and the event as JSON. list, add, remove, enable and " +
                "disable manage them in the config. A run takes a minute or more, so a " +
                "trigger cannot react within seconds. A datapack function can.",
        input =
            input {
                optional("name", stringSchema("The trigger's name.", maxLength = 64))
                optional("prompt", stringSchema("For add: what the agent does on each run."))
                optional(
                    "events",
                    arraySchema(enumSchema(EVENT_NAMES), "For add: the events that start a run."),
                )
                optional(
                    "every_minutes",
                    integerSchema("For add: start a run this often instead of on events.", min = 1),
                )
                optional(
                    "players",
                    arraySchema(
                        stringSchema(),
                        "For add: only events about these players, by name or UUID. Empty " +
                            "means anyone.",
                        default = EMPTY_ARRAY,
                    ),
                )
                optional(
                    "cooldown_seconds",
                    integerSchema(
                        "For add: the shortest time between two runs on events.",
                        min = 0,
                        default = 60,
                    ),
                )
                optional(
                    "announce",
                    booleanSchema(
                        "For add: post the answers in the chat, or only log them.",
                        default = true,
                    ),
                )
            },
        actions =
            mapOf(
                "list" to
                    readAction { _, _ ->
                        val triggers = currentTriggers()
                        lines(
                            "${triggers.size} triggers",
                            triggers.map(::describe),
                            dataOf("triggers" to encoded(triggers)),
                        )
                    },
                "add" to
                    writeAction { _, input ->
                        input.require("name", "prompt")
                        val trigger =
                            TriggerConfig(
                                name = input.string("name"),
                                events = input.strings("events"),
                                players = input.strings("players"),
                                everyMinutes = input.intOrNull("every_minutes") ?: 0,
                                prompt = input.string("prompt"),
                                cooldownSeconds = input.int("cooldown_seconds"),
                                announce = input.boolean("announce"),
                            )
                        changeConfig { it.addingTrigger(trigger) }
                        done(
                            "Trigger added. Each run takes as long as a request, so tell the " +
                                "player if the trigger needs to react within seconds.",
                        )
                    },
                "remove" to
                    destructiveAction { _, input ->
                        changeConfig { it.removingTrigger(input.string("name")) }
                        done("Trigger removed")
                    },
                "enable" to
                    writeAction { _, input ->
                        changeConfig { it.enablingTrigger(input.string("name"), enabled = true) }
                        done("Trigger enabled")
                    },
                "disable" to
                    writeAction { _, input ->
                        changeConfig { it.enablingTrigger(input.string("name"), enabled = false) }
                        done("Trigger disabled")
                    },
            ),
        // A trigger runs the agent again and again without anyone asking.
        needApproval = setOf("add"),
    )

private fun describe(trigger: TriggerConfig): String {
    val state = if (trigger.enabled) "on" else "off"
    val start =
        if (trigger.everyMinutes > 0) "every ${trigger.everyMinutes} min"
        else trigger.events.joinToString(", ") + ", cooldown ${trigger.cooldownSeconds} s"
    val players = trigger.players.takeIf { it.isNotEmpty() }?.joinToString(", ", " for ").orEmpty()
    val announce = if (trigger.announce) "announced" else "logged"
    return "${trigger.name} ($state): $start$players, $announce. Prompt: ${trigger.prompt}"
}
