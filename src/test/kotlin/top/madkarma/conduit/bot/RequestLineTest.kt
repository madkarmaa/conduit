package top.madkarma.conduit.bot

import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RequestLineTest {
    private val alex = Asker("Alex", UUID(0, 1))
    private val steve = Asker("Steve", UUID(0, 2))

    // Like the bot's own scope, where a failed answer does not stop the other requests.
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> },
        )

    // Each answer reports that it started, then waits until the test lets it finish.
    private val started = Channel<String>(Channel.UNLIMITED)
    private val finish = Channel<Unit>(Channel.UNLIMITED)
    private val line =
        RequestLine(scope) { request ->
            started.send(request.text)
            if (request.text == "broken") error("The agent failed")
            finish.receive()
        }

    @AfterTest
    fun stop() {
        scope.cancel()
    }

    private fun request(asker: Asker, text: String) = BotRequest(asker, text, Mode.FULL)

    private fun nextStarted(): String = runBlocking { withTimeout(5.seconds) { started.receive() } }

    @Test
    fun `answers one request at a time in the order they arrived`() {
        assertEquals(0, line.add(request(alex, "first")))
        assertEquals("first", nextStarted())
        assertEquals(1, line.add(request(steve, "second")))
        assertEquals(2, line.add(request(alex, "third")))

        assertEquals("first", line.running()?.text)
        assertEquals(listOf("second", "third"), line.waiting().map { it.text })

        finish.trySend(Unit)
        assertEquals("second", nextStarted())
        finish.trySend(Unit)
        assertEquals("third", nextStarted())
        assertEquals(emptyList(), line.waiting())
    }

    @Test
    fun `has looks at the running and the waiting requests`() {
        line.add(request(alex, "first"))
        nextStarted()
        line.add(request(steve, "second"))

        assertTrue(line.has { it.text == "first" })
        assertTrue(line.has { it.text == "second" })
        assertFalse(line.has { it.text == "third" })
    }

    @Test
    fun `cancel takes matching requests out of the line and stops the running one`() {
        line.add(request(alex, "first"))
        nextStarted()
        line.add(request(steve, "second"))
        line.add(request(alex, "third"))

        val cancelled = line.cancel { it.asker == alex }

        assertEquals(listOf("third", "first"), cancelled.map { it.text })
        assertEquals("second", nextStarted())
        assertEquals(emptyList(), line.waiting())
    }

    @Test
    fun `cancel leaves requests that do not match`() {
        line.add(request(alex, "first"))
        nextStarted()
        line.add(request(steve, "second"))

        assertEquals(emptyList(), line.cancel { it.asker.name == "Herobrine" })
        assertEquals("first", line.running()?.text)
        assertEquals(listOf("second"), line.waiting().map { it.text })
    }

    @Test
    fun `keeps answering after an answer fails`() {
        line.add(request(alex, "broken"))
        line.add(request(steve, "second"))

        assertEquals("broken", nextStarted())
        assertEquals("second", nextStarted())
    }
}
