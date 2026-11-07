package top.madkarma.conduit.service.world

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.border.WorldBorder
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.level

private val CENTER_RANGE = -WorldBorder.MAX_CENTER_COORDINATE..WorldBorder.MAX_CENTER_COORDINATE
private val SIZE_RANGE = 1.0..WorldBorder.MAX_SIZE

@Serializable
data class WorldBorderInfo(
    val centerX: Double,
    val centerZ: Double,
    /** Current side length in blocks. */
    val size: Double,
    /** Size the border is moving towards, or the current size when it is not moving. */
    val targetSize: Double,
    /** Time until the border reaches the target size, at the current tick rate. */
    val remainingTransitionMs: Long,
    val damagePerBlock: Double,
    /** Distance outside the border, in blocks, before players take damage. */
    val damageSafeZone: Double,
    val warningBlocks: Int,
    val warningTimeTicks: Int,
)

/** Null fields stay unchanged. */
data class WorldBorderChange(
    val dimension: String?,
    val centerX: Double? = null,
    val centerZ: Double? = null,
    val size: Double? = null,
    /**
     * Moves the border from its current size to size over this time, converted to ticks at the
     * current tick rate. 0 changes it instantly.
     */
    val transitionMs: Long = 0,
    val damagePerBlock: Double? = null,
    val damageSafeZone: Double? = null,
    val warningBlocks: Int? = null,
    val warningTimeTicks: Int? = null,
)

internal fun MinecraftServer.worldBorderOf(dimension: String?): WorldBorderInfo {
    val border = level(dimension).worldBorder
    val millisecondsPerTick = tickRateManager().millisecondsPerTick()

    return WorldBorderInfo(
        centerX = border.centerX,
        centerZ = border.centerZ,
        size = border.size,
        targetSize = border.lerpTarget,
        remainingTransitionMs = (border.lerpTime * millisecondsPerTick).roundToLong(),
        damagePerBlock = border.damagePerBlock,
        damageSafeZone = border.safeZone,
        warningBlocks = border.warningBlocks,
        warningTimeTicks = border.warningTime,
    )
}

internal fun MinecraftServer.changeWorldBorder(change: WorldBorderChange) {
    validateWorldBorder(change)

    val level = level(change.dimension)
    val border = level.worldBorder

    if (change.centerX != null || change.centerZ != null) {
        border.setCenter(change.centerX ?: border.centerX, change.centerZ ?: border.centerZ)
    }

    if (change.size != null) {
        val ticks = (change.transitionMs / tickRateManager().millisecondsPerTick()).roundToLong()

        if (ticks > 0) {
            border.lerpSizeBetween(border.size, change.size, ticks, level.gameTime)
        } else {
            border.size = change.size
        }
    }

    change.damagePerBlock?.let { border.damagePerBlock = it }
    change.damageSafeZone?.let { border.safeZone = it }
    change.warningBlocks?.let { border.warningBlocks = it }
    change.warningTimeTicks?.let { border.warningTime = it }
}

// This checks everything before the first change, so an invalid change changes nothing.
private fun validateWorldBorder(change: WorldBorderChange) {
    change.centerX?.let { requireInRange(it, CENTER_RANGE, "center_x") }
    change.centerZ?.let { requireInRange(it, CENTER_RANGE, "center_z") }
    change.size?.let { requireInRange(it, SIZE_RANGE, "size") }

    if (change.transitionMs < 0) invalidInput("transition_ms must not be negative")
    if (change.transitionMs > 0 && change.size == null) invalidInput("transition_ms requires size")

    change.damagePerBlock?.let { requireNonNegative(it, "damage_per_block") }
    change.damageSafeZone?.let { requireNonNegative(it, "damage_safe_zone") }

    if ((change.warningBlocks ?: 0) < 0) invalidInput("warning_blocks must not be negative")
    if ((change.warningTimeTicks ?: 0) < 0) invalidInput("warning_time_ticks must not be negative")
}

private fun requireInRange(value: Double, range: ClosedRange<Double>, name: String) {
    if (value in range) return
    invalidInput("$name must be between ${range.start} and ${range.endInclusive}, got $value")
}

private fun requireNonNegative(value: Double, name: String) {
    if (value >= 0.0 && value.isFinite()) return
    invalidInput("$name must be a finite number of at least 0, got $value")
}
