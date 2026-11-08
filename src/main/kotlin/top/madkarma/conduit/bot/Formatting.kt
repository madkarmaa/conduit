package top.madkarma.conduit.bot

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

// Minecraft's legacy formatting codes, which agents can use to color their answers.
private val COLORS: Map<Char, ChatFormatting> =
    "0123456789abcdef".associateWith { checkNotNull(ChatFormatting.getByCode(it)) }

private val STYLES: Map<Char, Pair<ChatFormatting, String>> =
    mapOf(
        'k' to (ChatFormatting.OBFUSCATED to "obfuscated"),
        'l' to (ChatFormatting.BOLD to "bold"),
        'm' to (ChatFormatting.STRIKETHROUGH to "strikethrough"),
        'n' to (ChatFormatting.UNDERLINE to "underlined"),
        'o' to (ChatFormatting.ITALIC to "italic"),
    )

/** Lists every code for the agent, such as "§c red, §l bold". */
val CODE_LIST: String =
    (COLORS.map { (code, color) -> "§$code ${color.name.lowercase().replaceFirst('_', ' ')}" } +
            STYLES.map { (code, style) -> "§$code ${style.second}" } +
            "§r reset")
        .joinToString(", ")

/**
 * Turns text with § codes, such as "§cRed §lbold", into styled components. As in Java Edition, a
 * color code also ends the styles before it, and §r ends everything. Unknown codes stay in the
 * text, and this drops a § at the end.
 */
fun formattedText(text: String): MutableComponent {
    val result = Component.empty()
    val segment = StringBuilder()
    var style = Style.EMPTY
    var index = 0

    fun startSegment(next: Style) {
        if (segment.isNotEmpty())
            result.append(Component.literal(segment.toString()).withStyle(style))
        segment.clear()
        style = next
    }

    while (index < text.length) {
        val character = text[index]
        if (character != '§' || index == text.length - 1) {
            if (character != '§') segment.append(character)
            index++
            continue
        }

        when (val code = text[index + 1].lowercaseChar()) {
            in COLORS -> startSegment(Style.EMPTY.applyFormat(COLORS.getValue(code)))
            in STYLES -> startSegment(style.applyFormat(STYLES.getValue(code).first))
            'r' -> startSegment(Style.EMPTY)
            else -> {
                // The character after an unknown code can start a code of its own.
                segment.append(character)
                index++
                continue
            }
        }
        index += 2
    }

    startSegment(Style.EMPTY)
    return result
}

/**
 * Agents tend to answer in Markdown, which the chat shows as literal symbols. This drops it, along
 * with empty lines, and shortens the text to maxLength.
 */
fun toChatText(answer: String, maxLength: Int): String {
    val text =
        answer
            .replace(Regex("\\*\\*|__|`"), "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    if (text.length <= maxLength) return text
    return text.take(maxLength - 1).trimEnd() + "…"
}
