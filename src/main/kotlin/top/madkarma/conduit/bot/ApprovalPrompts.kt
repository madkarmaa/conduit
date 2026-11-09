package top.madkarma.conduit.bot

import java.util.*
import net.minecraft.ChatFormatting
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import top.madkarma.conduit.LOGGER
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.CalledAction

private const val ARGUMENTS_LENGTH = 200

/** Sends a message to the asker and every online operator, who decide on approvals. */
internal fun MinecraftServer.tellAudience(asker: Asker, message: Component) {
    audience(asker).forEach { it.sendSystemMessage(message) }
}

/** Sends the prompt again with a sound, since chat scrolls past a prompt that waits long. */
internal fun MinecraftServer.remindAudience(asker: Asker, message: Component) {
    for (player in audience(asker)) {
        player.sendSystemMessage(message)
        player.connection.send(
            ClientboundSoundPacket(
                SoundEvents.NOTE_BLOCK_PLING,
                SoundSource.MASTER,
                player.x,
                player.y,
                player.z,
                1f,
                1f,
                player.random.nextLong(),
            ),
        )
    }
}

private fun MinecraftServer.audience(asker: Asker): List<ServerPlayer> =
    playerList.players.filter { it.uuid == asker.uuid || playerList.isOp(it.nameAndId()) }

/** The approval prompt, with `[Approve]`, `[Approve all]` and `[Deny]` buttons with the nonce. */
internal fun prompt(
    nonce: String,
    asker: Asker,
    actions: String,
    calls: List<CalledAction>,
    timeoutSeconds: Int,
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
            .append(
                button(
                    "approve_all",
                    nonce,
                    "[Approve all]",
                    ChatFormatting.GOLD,
                    "Run it and the rest of this request without asking again",
                ),
            )
            .append(" ")
            .append(button("deny", nonce, "[Deny]", ChatFormatting.RED, "Do not run it"))
            .append(
                Component.literal(" $timeoutSeconds s to answer").withStyle(ChatFormatting.GRAY),
            ),
    )
}

/** Tells the audience how the approval ended, such as "Alex approved run_command.run." */
internal suspend fun announce(asker: Asker, outcome: String) {
    LOGGER.info("{} This was the request of {}.", outcome, asker.name)
    val message = botMessage(Component.literal(outcome).withStyle(ChatFormatting.GRAY))
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
