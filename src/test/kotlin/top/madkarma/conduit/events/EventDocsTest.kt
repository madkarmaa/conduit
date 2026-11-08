package top.madkarma.conduit.events

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class EventDocsTest {
    @Test
    fun `the README lists every event type`() {
        val readme = Files.readString(Path.of(System.getProperty("conduit.readme")))
        val documented =
            Regex("""^\| `([a-z_]+)` +\|""", RegexOption.MULTILINE)
                .findAll(readme)
                .map { it.groupValues[1] }
                .toSet()

        val missing = EventType.entries.map { it.id }.filterNot { it in documented }
        assertEquals(emptyList(), missing, "Event types missing from the README's trigger section")
    }
}
