package top.madkarma.conduit.service.player

import java.util.UUID
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import top.madkarma.conduit.game.UndoableChange
import top.madkarma.conduit.game.failedPrecondition

/**
 * The criteria of an advancement that a recorded call granted or revoked. Undo reverses exactly
 * those, which the game only allows while the player is online.
 */
class AdvancementChange(
    override val subject: UUID,
    private val advancement: AdvancementHolder,
    private val criteria: List<String>,
    private val granted: Boolean,
) : UndoableChange {
    override fun changed(): Boolean = criteria.isNotEmpty()

    override fun checkUndo(server: MinecraftServer) {
        if (server.playerList.getPlayer(subject) != null) return
        failedPrecondition(
            "${server.lastKnownName(subject).ifEmpty { "$subject" }} is offline. Undo their " +
                "advancement changes once they are back.",
        )
    }

    override fun undo(server: MinecraftServer) {
        val advancements = server.playerList.getPlayer(subject)?.advancements ?: return
        criteria.forEach {
            if (granted) advancements.revoke(advancement, it)
            else advancements.award(advancement, it)
        }
    }

    companion object {
        /** The criteria of the list that a grant or revoke would change. */
        fun before(
            player: ServerPlayer,
            advancement: AdvancementHolder,
            criteria: List<String>,
            granting: Boolean,
        ): AdvancementChange {
            val progress = player.advancements.getOrStartProgress(advancement)
            val changing = criteria.filter {
                (progress.getCriterion(it)?.isDone == true) != granting
            }
            return AdvancementChange(player.uuid, advancement, changing, granting)
        }
    }
}
