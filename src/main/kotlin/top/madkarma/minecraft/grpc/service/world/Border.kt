package top.madkarma.minecraft.grpc.service.world

import kotlin.math.roundToLong
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.border.WorldBorder
import top.madkarma.minecraft.grpc.game.level
import top.madkarma.minecraft.grpc.proto.DimensionRef
import top.madkarma.minecraft.grpc.proto.SetWorldBorderRequest
import top.madkarma.minecraft.grpc.proto.WorldBorderInfo
import top.madkarma.minecraft.grpc.proto.worldBorderInfo
import top.madkarma.minecraft.grpc.rpc.invalidArgument

private val CENTER_RANGE = -WorldBorder.MAX_CENTER_COORDINATE..WorldBorder.MAX_CENTER_COORDINATE
private val SIZE_RANGE = 1.0..WorldBorder.MAX_SIZE

internal fun MinecraftServer.worldBorderOf(request: DimensionRef): WorldBorderInfo {
    val border = level(request.dimension).worldBorder
    val millisecondsPerTick = tickRateManager().millisecondsPerTick()

    return worldBorderInfo {
        centerX = border.centerX
        centerZ = border.centerZ
        size = border.size
        targetSize = border.lerpTarget
        remainingTransitionMs = (border.lerpTime * millisecondsPerTick).roundToLong()
        damagePerBlock = border.damagePerBlock
        damageSafeZone = border.safeZone
        warningBlocks = border.warningBlocks
        warningTimeTicks = border.warningTime
    }
}

internal fun MinecraftServer.changeWorldBorder(request: SetWorldBorderRequest) {
    validateWorldBorder(request)

    val level = level(request.dimension)
    val border = level.worldBorder

    if (request.hasCenterX() || request.hasCenterZ()) {
        val centerX = if (request.hasCenterX()) request.centerX else border.centerX
        val centerZ = if (request.hasCenterZ()) request.centerZ else border.centerZ
        border.setCenter(centerX, centerZ)
    }

    if (request.hasSize()) {
        val ticks = (request.transitionMs / tickRateManager().millisecondsPerTick()).roundToLong()

        if (ticks > 0) {
            border.lerpSizeBetween(border.size, request.size, ticks, level.gameTime)
        } else {
            border.size = request.size
        }
    }

    if (request.hasDamagePerBlock()) border.damagePerBlock = request.damagePerBlock
    if (request.hasDamageSafeZone()) border.safeZone = request.damageSafeZone
    if (request.hasWarningBlocks()) border.warningBlocks = request.warningBlocks
    if (request.hasWarningTimeTicks()) border.warningTime = request.warningTimeTicks
}

// Everything is checked before the first change, so an invalid request changes nothing.
private fun validateWorldBorder(request: SetWorldBorderRequest) {
    if (request.hasCenterX()) requireInRange(request.centerX, CENTER_RANGE, "center_x")
    if (request.hasCenterZ()) requireInRange(request.centerZ, CENTER_RANGE, "center_z")
    if (request.hasSize()) requireInRange(request.size, SIZE_RANGE, "size")

    if (request.transitionMs < 0) invalidArgument("transition_ms must not be negative")
    if (request.transitionMs > 0 && !request.hasSize()) {
        invalidArgument("transition_ms requires size")
    }

    if (request.hasDamagePerBlock()) requireNonNegative(request.damagePerBlock, "damage_per_block")
    if (request.hasDamageSafeZone()) requireNonNegative(request.damageSafeZone, "damage_safe_zone")

    if (request.warningBlocks < 0) invalidArgument("warning_blocks must not be negative")
    if (request.warningTimeTicks < 0) invalidArgument("warning_time_ticks must not be negative")
}

private fun requireInRange(value: Double, range: ClosedRange<Double>, name: String) {
    if (value in range) return
    invalidArgument("$name must be between ${range.start} and ${range.endInclusive}, got $value")
}

private fun requireNonNegative(value: Double, name: String) {
    if (value >= 0.0 && value.isFinite()) return
    invalidArgument("$name must be a finite number of at least 0, got $value")
}
