package top.madkarma.conduit.bot

import java.util.*
import net.minecraft.ChatFormatting
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.CalledAction

private val LOGGER = LoggerFactory.getLogger("conduit")

private const val ARGUMENTS_LENGTH = 200

/** Sends a message to the asker and every online operator, who decide on approvals. */
internal fun MinecraftServer.tellAudience(asker: Asker, message: Component) {
    playerList.players
        .filter { it.uuid == asker.uuid || playerList.isOp(it.nameAndId()) }
        .forEach { it.sendSystemMessage(message) }
}

/** The approval prompt, with `[Approve]` and `[Deny]` buttons that carry the nonce. */
internal fun prompt(
    nonce: String,
    asker: Asker,
    actions: String,
    calls: List<CalledAction>,
): Component {
    val details = calls.joinToString(" ") { it.arguments.toString() }
    val shown =
        if (details.length > ARGUMENTS_LENGTH) details.take(ARGUMENTS_LENGTH) + "…" else details

    return botMessage(
        Component.literal("${asker.name}'s request wants to use $actions ")
            .append(Component.literal(shown).withStyle(ChatFormatting.GRAY))
            .append("\n")
            .append(button("approve", nonce, "[Approve]", ChatFormatting.GREEN, "Run it"))
            .append(" ")
            .append(button("deny", nonce, "[Deny]", ChatFormatting.RED, "Do not run it")),
    )
}

/**
 * Tells the audience how the approval ended. The decision is who decided and whether they approved.
 */
internal suspend fun announce(asker: Asker, actions: String, decision: Pair<String, Boolean>?) {
    val outcome =
        when {
            decision == null -> "Nobody answered"
            decision.second -> "${decision.first} approved"
            else -> "${decision.first} denied"
        }

    LOGGER.info("{} for {} of {}", outcome, actions, asker.name)
    val message = botMessage(Component.literal("$outcome $actions.").withStyle(ChatFormatting.GRAY))
    GameServer.run { it.tellAudience(asker, message) }
}

private fun button(
    choice: String,
    nonce: String,
    label: String,
    color: ChatFormatting,
    hover: String,
) =
    Component.literal(label).withStyle { style ->
        style
            .withColor(color)
            .withBold(true)
            .withClickEvent(
                ClickEvent.Custom(CLICK_ID, Optional.of(StringTag.valueOf("$choice:$nonce"))),
            )
            .withHoverEvent(HoverEvent.ShowText(Component.literal(hover)))
    }
