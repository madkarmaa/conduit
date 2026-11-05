package top.madkarma.minecraft.grpc.client.service

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.ArrayDeque

object ChatLog {
    private const val CAPACITY = 500
    private val messages = ArrayDeque<String>()

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) add(message.string)
        }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> add(message.string) }
    }

    @Synchronized
    fun recent(limit: Int): List<String> =
        messages.toList().takeLast(if (limit > 0) limit else CAPACITY)

    @Synchronized
    private fun add(message: String) {
        if (messages.size == CAPACITY) messages.removeFirst()
        messages.addLast(message)
    }
}
