package top.madkarma.conduit.bot

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * A message from the bot, after "[Conduit]". The content goes into a white component of its own, so
 * its own colors still apply.
 */
fun botMessage(content: Component): Component =
    Component.literal("[Conduit] ")
        .withStyle(ChatFormatting.AQUA)
        .append(Component.empty().withStyle(ChatFormatting.WHITE).append(content))

fun botMessage(text: String): Component = botMessage(Component.literal(text))

/** A player's request, as everyone sees it. */
fun questionMessage(asker: Asker, request: String): Component =
    Component.literal("[${asker.name} → Conduit] ")
        .withStyle(ChatFormatting.AQUA)
        .append(Component.literal(request).withStyle(ChatFormatting.WHITE))
