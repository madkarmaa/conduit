package top.madkarma.conduit.service.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3

// As long as the portal tickets last, which keep both ends loaded meanwhile.
private const val MAX_WAIT_TICKS = 15 * 20

// Like a portal, which keeps the chunks around its ends ticking for the entities that pass.
private const val TICKET_RADIUS = 3

/**
 * Vehicles that follow their riders to a chunk that was not loaded. A vehicle moved into such a
 * chunk unloads at once and throws off its riders, so the riders go first and load the chunk, and
 * the vehicle follows and takes them back once its entities tick. All access happens on the server
 * thread.
 */
object RiderTeleports {
    private class Follow(
        val vehicle: Entity,
        val riders: List<ServerPlayer>,
        val level: ServerLevel,
        val target: Vec3,
        val endsAtTick: Int,
    )

    private val follows = mutableListOf<Follow>()

    /** Sends the vehicle's riders to the target now, and the vehicle after them. */
    fun start(server: MinecraftServer, vehicle: Entity, level: ServerLevel, target: Vec3) {
        // Without its riders, the vehicle's own chunk would unload and save it away meanwhile.
        val origin = vehicle.level() as ServerLevel
        origin.chunkSource.addTicketWithRadius(
            TicketType.PORTAL,
            vehicle.chunkPosition(),
            TICKET_RADIUS,
        )
        level.chunkSource.addTicketWithRadius(
            TicketType.PORTAL,
            ChunkPos.containing(BlockPos.containing(target)),
            TICKET_RADIUS,
        )
        val riders = vehicle.passengers.filterIsInstance<ServerPlayer>()
        for (rider in riders) {
            rider.stopRiding()
            rider.teleportTo(
                level,
                target.x,
                target.y,
                target.z,
                emptySet(),
                rider.yRot,
                rider.xRot,
                true,
            )
        }
        follows += Follow(vehicle, riders, level, target, server.tickCount + MAX_WAIT_TICKS)
    }

    fun clear() {
        follows.clear()
    }

    fun onTick(server: MinecraftServer) {
        if (follows.isEmpty()) return
        follows.removeIf { follow ->
            when {
                follow.vehicle.isRemoved || server.tickCount >= follow.endsAtTick -> true
                follow.level.isPositionEntityTicking(BlockPos.containing(follow.target)) ->
                    arrive(follow).let { true }
                else -> false
            }
        }
    }

    private fun arrive(follow: Follow) {
        val vehicle = follow.vehicle
        val target = vehicle.fittingPosition(follow.level, follow.target) ?: follow.target
        vehicle.teleportTo(
            follow.level,
            target.x,
            target.y,
            target.z,
            emptySet(),
            vehicle.yRot,
            vehicle.xRot,
            true,
        )
        // The game recreates a vehicle that changed dimension.
        val arrived = follow.level.getEntity(vehicle.uuid) ?: return
        follow.riders
            .filter { !it.isRemoved && it.level() === follow.level }
            .forEach {
                it.startRiding(arrived, true, true)
            }
    }
}
