package top.madkarma.minecraft.grpc.events

import java.util.*
import kotlin.math.abs
import kotlin.math.max
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import top.madkarma.minecraft.grpc.proto.EventType

data class PlayerMove(
    val dimension: ResourceKey<Level>,
    val position: Vec3,
    val yaw: Float,
    val pitch: Float,
    val tick: Int,
)

/** Publishes EVENT_TYPE_PLAYER_MOVE by comparing each player with what each subscription saw. */
object PlayerMoves {
    // Players the server moved during the current tick, as opposed to moving by themselves.
    private val teleportedPlayers = HashSet<UUID>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::publishMoves)

        ServerPlayerEvents.LEAVE.register { player ->
            for (subscription in EventBus.subscribers(EventType.EVENT_TYPE_PLAYER_MOVE)) {
                subscription.sentMoves.remove(player.uuid)
            }
        }
    }

    // Called from the teleport mixin.
    @JvmStatic
    fun onTeleported(player: ServerPlayer) {
        if (!EventBus.wants(EventType.EVENT_TYPE_PLAYER_MOVE)) return
        teleportedPlayers += player.uuid
    }

    private fun publishMoves(server: MinecraftServer) {
        val subscribers = EventBus.subscribers(EventType.EVENT_TYPE_PLAYER_MOVE)

        if (subscribers.isNotEmpty()) {
            for (player in server.playerList.players) {
                publishMove(player, subscribers, server.tickCount)
            }
        }

        teleportedPlayers.clear()
    }

    private fun publishMove(player: ServerPlayer, subscribers: List<EventSubscription>, tick: Int) {
        val move =
            PlayerMove(
                player.level().dimension(),
                player.position(),
                player.yRot,
                player.xRot,
                tick,
            )

        val event by lazy {
            EventBus.buildEvent {
                playerMove = movePayload(player, player.uuid in teleportedPlayers)
            }
        }

        for (subscription in subscribers) {
            if (!subscription.wantsPlayer(player)) continue

            val lastSent = subscription.sentMoves[player.uuid]
            if (lastSent != null && !subscription.shouldSend(lastSent, move)) continue

            subscription.sentMoves[player.uuid] = move
            subscription.send(event)
        }
    }

    private fun EventSubscription.shouldSend(lastSent: PlayerMove, move: PlayerMove): Boolean {
        if (move.dimension != lastSent.dimension) return true
        if (move.position.distanceTo(lastSent.position) >= moveMinDistance) return true

        val turned =
            max(
                Mth.degreesDifferenceAbs(lastSent.yaw, move.yaw),
                abs(move.pitch - lastSent.pitch),
            )

        return moveMinRotation > 0 && turned >= moveMinRotation ||
            moveHeartbeatTicks > 0 && move.tick - lastSent.tick >= moveHeartbeatTicks
    }
}
