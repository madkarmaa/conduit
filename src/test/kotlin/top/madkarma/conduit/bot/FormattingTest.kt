package top.madkarma.conduit.bot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.network.chat.Component

class FormattingTest {
    // Each styled part of the text, with its color and styles, such as "Red " to "red bold".
    private fun segments(text: String): List<Pair<String, String>> =
        formattedText(text).siblings.map { it.string to styleOf(it) }

    private fun styleOf(component: Component): String {
        val style = component.style
        return listOfNotNull(
                style.color?.serialize(),
                "bold".takeIf { style.isBold },
                "italic".takeIf { style.isItalic },
                "underlined".takeIf { style.isUnderlined },
                "strikethrough".takeIf { style.isStrikethrough },
                "obfuscated".takeIf { style.isObfuscated },
            )
            .joinToString(" ")
    }

    @Test
    fun `plain text stays one unstyled part`() {
        assertEquals(listOf("Hello" to ""), segments("Hello"))
    }

    @Test
    fun `styles add to the color before them`() {
        assertEquals(listOf("Red " to "red", "bold" to "red bold"), segments("§cRed §lbold"))
    }

    @Test
    fun `a color code ends the styles before it`() {
        assertEquals(listOf("bold" to "bold", "red" to "red"), segments("§lbold§cred"))
    }

    @Test
    fun `reset ends the color and every style`() {
        assertEquals(
            listOf("both" to "green italic underlined", " plain" to ""),
            segments("§a§o§nboth§r plain"),
        )
    }

    @Test
    fun `codes may be upper case`() {
        assertEquals(
            listOf("Blue" to "blue", "struck" to "blue strikethrough"),
            segments("§9Blue§Mstruck"),
        )
    }

    @Test
    fun `unknown codes stay in the text`() {
        assertEquals(listOf("§zoom" to ""), segments("§zoom"))
    }

    @Test
    fun `the character after an unknown code can start a code of its own`() {
        assertEquals(listOf("§" to "", "red" to "red"), segments("§§cred"))
    }

    @Test
    fun `a section sign at the end is dropped`() {
        assertEquals(listOf("end" to ""), segments("end§"))
    }

    @Test
    fun `codes without text leave no parts`() {
        assertEquals(emptyList(), segments("§c§l"))
    }

    @Test
    fun `the code list names every color and style`() {
        val codes = CODE_LIST.split(", ")

        assertEquals(22, codes.size)
        assertEquals("§0 black", codes.first())
        assertEquals("§r reset", codes.last())
        assertTrue("§1 dark blue" in codes)
        assertTrue("§d light purple" in codes)
        assertTrue("§k obfuscated" in codes)
    }

    @Test
    fun `chat text drops Markdown emphasis, code marks and empty lines`() {
        assertEquals(
            "Hello world\nthere *you*",
            toChatText("**Hello** `world`\n\n   __there__ *you*  \n", 100),
        )
    }

    @Test
    fun `chat text keeps text that fits`() {
        assertEquals("abcde", toChatText("abcde", 5))
    }

    @Test
    fun `chat text ends a shortened answer with an ellipsis within the limit`() {
        assertEquals("abcd…", toChatText("abcdefghij", 5))
        assertEquals("abc…", toChatText("abc defgh", 5))
    }
}
