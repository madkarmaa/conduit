package top.madkarma.conduit.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementType
import net.minecraft.server.level.ServerPlayer

@Serializable
enum class AdvancementFrame {
    @SerialName("task") TASK,
    @SerialName("goal") GOAL,
    @SerialName("challenge") CHALLENGE,
}

/** A player completed an advancement. */
@Serializable
data class AdvancementEvent(
    val player: String,
    val playerUuid: String,
    val id: String,
    /** Title and frame are only set for advancements with a display. */
    val title: String?,
    val frame: AdvancementFrame?,
    /** The game announces it in chat, unless the game rule for advancement messages is off. */
    val announced: Boolean,
)

/** Publishes ADVANCEMENT. */
object Advancements {
    // Called from the advancement mixin, only when an advancement becomes done.
    @JvmStatic
    fun onAdvancementDone(player: ServerPlayer, advancement: AdvancementHolder) {
        // Players earn recipe advancements constantly by picking up items.
        if (advancement.id().path.startsWith("recipes/")) return

        EventBus.publish(EventType.ADVANCEMENT) { advancementPayload(player, advancement) }
    }

    private fun advancementPayload(
        player: ServerPlayer,
        advancement: AdvancementHolder,
    ): AdvancementEvent {
        val display = advancement.value().display().orElse(null)

        return AdvancementEvent(
            player = player.gameProfile.name,
            playerUuid = player.stringUUID,
            id = advancement.id().toString(),
            title = display?.title()?.string,
            frame = display?.let { frameOf(it.type()) },
            announced = display?.announceToChat() ?: false,
        )
    }

    private fun frameOf(type: AdvancementType): AdvancementFrame =
        when (type) {
            AdvancementType.TASK -> AdvancementFrame.TASK
            AdvancementType.GOAL -> AdvancementFrame.GOAL
            AdvancementType.CHALLENGE -> AdvancementFrame.CHALLENGE
        }
}
