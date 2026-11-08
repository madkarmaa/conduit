package top.madkarma.conduit.config

import kotlin.test.Test
import kotlin.test.assertEquals

class TriggerChecksTest {
    private val welcome =
        TriggerConfig(
            name = "welcome",
            events = listOf("player_join"),
            prompt = "Greet the player.",
        )
    private val hourly = TriggerConfig(name = "hourly", everyMinutes = 60, prompt = "Post a tip.")

    private fun problemsWith(vararg triggers: TriggerConfig): List<String> =
        VALID_CONFIG.copy(triggers = triggers.toList()).triggerProblems()

    @Test
    fun `event and timed triggers have no problems`() {
        assertEquals(emptyList(), problemsWith(welcome, hourly))
    }

    @Test
    fun `a trigger needs a name, a prompt and limits that are not negative`() {
        val broken = welcome.copy(name = " ", prompt = "", cooldownSeconds = -1)

        val expected =
            listOf(
                "triggers[0].name must not be empty",
                "triggers[0].prompt must not be empty",
                "triggers[0].cooldownSeconds must not be negative",
            )
        assertEquals(expected, problemsWith(broken))
    }

    @Test
    fun `a negative timer is neither a timer nor an event trigger`() {
        val expected =
            listOf(
                "triggers[0].everyMinutes must not be negative",
                "triggers[0] needs either events or everyMinutes",
            )
        assertEquals(expected, problemsWith(hourly.copy(everyMinutes = -5)))
    }

    @Test
    fun `a trigger has either events or a timer`() {
        assertEquals(
            listOf("triggers[0] needs either events or everyMinutes"),
            problemsWith(welcome.copy(everyMinutes = 5)),
        )
        assertEquals(
            listOf("triggers[0] needs either events or everyMinutes"),
            problemsWith(welcome.copy(events = emptyList())),
        )
    }

    @Test
    fun `events must be known event types`() {
        val trigger = welcome.copy(events = listOf("player_join", "player_dance"))

        assertEquals(listOf("triggers[0] names unknown event player_dance"), problemsWith(trigger))
    }

    @Test
    fun `trigger names must not repeat`() {
        assertEquals(
            listOf("triggers names repeat"),
            problemsWith(welcome, hourly.copy(name = "welcome")),
        )
    }

    @Test
    fun `enabled triggers need full mode`() {
        val config = VALID_CONFIG.withFullAgent { copy(enabled = false) }

        assertEquals(
            listOf("triggers run in full mode, so agents.full must be enabled"),
            config.copy(triggers = listOf(welcome)).triggerProblems(),
        )
        assertEquals(
            emptyList(),
            config.copy(triggers = listOf(welcome.copy(enabled = false))).triggerProblems(),
        )
    }

    @Test
    fun `problems includes the trigger problems`() {
        val config = VALID_CONFIG.copy(triggers = listOf(welcome.copy(prompt = "")))

        assertEquals(listOf("triggers[0].prompt must not be empty"), config.problems(TOOL_ACTIONS))
    }
}
