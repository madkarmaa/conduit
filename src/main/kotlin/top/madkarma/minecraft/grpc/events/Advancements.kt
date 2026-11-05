package top.madkarma.minecraft.grpc.events

import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementType
import net.minecraft.server.level.ServerPlayer
import top.madkarma.minecraft.grpc.proto.AdvancementEvent
import top.madkarma.minecraft.grpc.proto.AdvancementFrame
import top.madkarma.minecraft.grpc.proto.EventType
import top.madkarma.minecraft.grpc.proto.advancementEvent

/** Publishes EVENT_TYPE_ADVANCEMENT. */
object Advancements {
    // Called from the advancement mixin, only when an advancement becomes done.
    @JvmStatic
    fun onAdvancementDone(player: ServerPlayer, advancement: AdvancementHolder) {
        if (!EventBus.wants(EventType.EVENT_TYPE_ADVANCEMENT)) return

        val isRecipe = advancement.id().path.startsWith("recipes/")
        EventBus.publish(
            EventType.EVENT_TYPE_ADVANCEMENT,
            accepts = { it.wantsPlayer(player) && (!isRecipe || it.includeRecipeAdvancements) },
        ) {
            this.advancement = advancementPayload(player, advancement)
        }
    }

    private fun advancementPayload(
        player: ServerPlayer,
        advancement: AdvancementHolder,
    ): AdvancementEvent = advancementEvent {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID
        id = advancement.id().toString()

        advancement.value().display().ifPresent { display ->
            title = display.title().string
            frame = frameOf(display.type())
            announced = display.announceToChat()
        }
    }

    private fun frameOf(type: AdvancementType): AdvancementFrame = when (type) {
        AdvancementType.TASK -> AdvancementFrame.ADVANCEMENT_FRAME_TASK
        AdvancementType.GOAL -> AdvancementFrame.ADVANCEMENT_FRAME_GOAL
        AdvancementType.CHALLENGE -> AdvancementFrame.ADVANCEMENT_FRAME_CHALLENGE
    }
}
