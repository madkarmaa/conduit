package top.madkarma.conduit.service.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import top.madkarma.conduit.config.CONFIG_JSON
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.TriggerConfig
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.invalidInputMessage
import top.madkarma.conduit.parseJson

class TriggerEditsTest {
    // A config file as an operator wrote it, with a variable that the edits must keep.
    private val written =
        parseJson(
            $$"""
            {
                "telemetry": true,
                "triggers": [{"name": "news", "everyMinutes": 60, "prompt": "${NEWS_PROMPT}"}]
            }
            """,
        )

    private fun triggersOf(json: JsonObject): List<TriggerConfig> =
        CONFIG_JSON.decodeFromJsonElement<ConduitConfig>(json).triggers

    @Test
    fun `add appends the trigger and keeps the rest of the file`() {
        val greeter = TriggerConfig(name = "greeter", events = listOf("player_join"), prompt = "Hi")

        val changed = written.addingTrigger(greeter)

        assertEquals(written["telemetry"], changed["telemetry"])
        assertEquals(listOf("news", "greeter"), triggersOf(changed).map { it.name })
        assertEquals(greeter, triggersOf(changed)[1])
        assertEquals($$"${NEWS_PROMPT}", triggersOf(changed)[0].prompt)
    }

    @Test
    fun `add refuses a name that is taken`() {
        val message = invalidInputMessage {
            written.addingTrigger(TriggerConfig(name = "news", everyMinutes = 5, prompt = "Hi"))
        }

        assertEquals("A trigger named news exists. Remove it or pick another name", message)
    }

    @Test
    fun `add works on a file without triggers`() {
        val changed =
            parseJson("{}")
                .addingTrigger(TriggerConfig(name = "a", everyMinutes = 5, prompt = "Hi"))

        assertEquals(listOf("a"), triggersOf(changed).map { it.name })
    }

    @Test
    fun `remove deletes the named trigger`() {
        assertEquals(emptyList(), triggersOf(written.removingTrigger("news")))
    }

    @Test
    fun `enable and disable change only the named trigger`() {
        val disabled =
            written
                .addingTrigger(TriggerConfig(name = "b", everyMinutes = 5, prompt = "Hi"))
                .enablingTrigger("news", enabled = false)

        assertEquals(listOf(false, true), triggersOf(disabled).map { it.enabled })
        assertEquals(
            listOf(true, true),
            triggersOf(disabled.enablingTrigger("news", enabled = true)).map { it.enabled },
        )
    }

    @Test
    fun `a missing trigger is not found`() {
        val error = assertFailsWith<ConduitError.NotFound> { written.removingTrigger("nope") }
        assertEquals("No trigger is named nope: news", error.message)

        val none =
            assertFailsWith<ConduitError.NotFound> {
                parseJson("{}").enablingTrigger("nope", enabled = true)
            }
        assertEquals("There are no triggers", none.message)
    }
}
