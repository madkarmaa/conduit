package top.madkarma.conduit.bot

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.RequestsConfig
import top.madkarma.conduit.config.TriggerConfig
import top.madkarma.conduit.events.EventType
import top.madkarma.conduit.events.GameEvent
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.parseJson
import top.madkarma.conduit.service.player.LookTarget
import top.madkarma.conduit.service.player.LookedAtEntity
import top.madkarma.conduit.service.player.PlayerPlace

class PromptTest {
    private val alex = Asker("Alex", UUID(0, 1))
    private val welcome =
        TriggerConfig(name = "welcome", events = listOf("player_join"), prompt = "Greet them.")
    private val joined = GameEvent(EventType.PLAYER_JOIN, 100, parseJson("""{"player": "Alex"}"""))

    @Test
    fun `chat mode tells the agent about the player's limited access`() {
        val config = ConduitConfig()

        assertTrue("Alex has limited access." in config.buildInstructions(Mode.CHAT, alex))
        assertFalse("limited access" in config.buildInstructions(Mode.FULL, alex))
    }

    @Test
    fun `the server's own instructions come last`() {
        val config = ConduitConfig(requests = RequestsConfig(instructions = "No griefing."))

        assertTrue(config.buildInstructions(Mode.FULL, alex).endsWith(" No griefing."))
    }

    @Test
    fun `a trigger run says that no player asked and whether anyone reads the answer`() {
        val config = ConduitConfig()
        val announced = config.buildInstructions(Mode.FULL, alex, TriggerRun(welcome, joined))
        val silent =
            config.buildInstructions(
                Mode.FULL,
                alex,
                TriggerRun(welcome.copy(announce = false), joined),
            )

        assertTrue("The server's trigger \"welcome\"" in announced)
        assertFalse("Nobody reads your final answer" in announced)
        assertTrue("Nobody reads your final answer" in silent)
    }

    @Test
    fun `the request names the player who asks`() {
        assertEquals(
            "Alex (UUID 00000000-0000-0000-0000-000000000001) asks: where am I?",
            requestPrompt(alex, "where am I?"),
        )
    }

    @Test
    fun `the request says where an online asker is and what they look at`() {
        val spawner =
            LookTarget(
                distance = 12.0,
                entity =
                    LookedAtEntity("00000000-0000-0000-0000-000000000002", "minecraft:pig", "Pig"),
            )
        val place = PlayerPlace("minecraft:the_nether", BlockCoordinates(506, 63, -262), spawner)

        assertEquals(
            "Alex (UUID 00000000-0000-0000-0000-000000000001) at minecraft:the_nether 506 63 -262, " +
                "looking at minecraft:pig \"Pig\" 00000000-0000-0000-0000-000000000002 asks: " +
                "which pig?",
            requestPrompt(alex, "which pig?", place),
        )
    }

    @Test
    fun `the request leaves out what an asker who looks at nothing looks at`() {
        val place = PlayerPlace("minecraft:overworld", BlockCoordinates(1, 64, -2), null)

        assertEquals(
            "Alex (UUID 00000000-0000-0000-0000-000000000001) at minecraft:overworld 1 64 -2 " +
                "asks: hi",
            requestPrompt(alex, "hi", place),
        )
    }

    @Test
    fun `the request after a cancelled one says not to continue it`() {
        assertEquals(
            "The player cancelled the previous request. Do not continue it unless this request " +
                "asks for it.\n\nAlex (UUID 00000000-0000-0000-0000-000000000001) asks: hi",
            requestPrompt(alex, "hi", afterCancel = true),
        )
    }

    @Test
    fun `a trigger prompt names the event or the timer that started it`() {
        assertEquals(
            "The trigger started because of the event player_join: {\"player\":\"Alex\"}\n\nGreet them.",
            triggerPrompt(TriggerRun(welcome, joined)),
        )

        val hourly = TriggerConfig(name = "hourly", everyMinutes = 60, prompt = "Post a tip.")
        assertEquals(
            "The trigger started because of its timer, every 60 minutes\n\nPost a tip.",
            triggerPrompt(TriggerRun(hourly, null)),
        )
    }

    @Test
    fun `each trigger runs as an asker of its own`() {
        val first = TriggerRun(welcome, joined).asker
        val again = TriggerRun(welcome, null).asker

        assertEquals("trigger welcome", first.name)
        assertEquals(first.uuid, again.uuid)
        assertFalse(first.uuid == TriggerRun(welcome.copy(name = "other"), null).asker.uuid)
    }
}
