package top.madkarma.minecraft.grpc.service.world

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.storage.LevelData
import top.madkarma.minecraft.grpc.game.dimensionKey
import top.madkarma.minecraft.grpc.game.enumByName
import top.madkarma.minecraft.grpc.game.level
import top.madkarma.minecraft.grpc.game.runCommandOrFail
import top.madkarma.minecraft.grpc.game.toMc
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.DimensionRef
import top.madkarma.minecraft.grpc.proto.ExplodeRequest
import top.madkarma.minecraft.grpc.proto.GameRules
import top.madkarma.minecraft.grpc.proto.SetGameRuleRequest
import top.madkarma.minecraft.grpc.proto.SetTimeRequest
import top.madkarma.minecraft.grpc.proto.SetWeatherRequest
import top.madkarma.minecraft.grpc.proto.SpawnPoint
import top.madkarma.minecraft.grpc.proto.TimeInfo
import top.madkarma.minecraft.grpc.proto.WeatherInfo
import top.madkarma.minecraft.grpc.proto.gameRules
import top.madkarma.minecraft.grpc.proto.spawnPoint
import top.madkarma.minecraft.grpc.proto.timeInfo
import top.madkarma.minecraft.grpc.proto.weatherInfo
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.notFound

// Time and weather changes go through the vanilla commands, which keep world clocks and clients
// in sync.

private const val TICKS_PER_DAY = 24000

internal fun MinecraftServer.timeOf(request: DimensionRef): TimeInfo {
    val level = level(request.dimension)
    val clock = level.defaultClockTime
    return timeInfo {
        gameTime = level.gameTime
        dayTime = clock % TICKS_PER_DAY
        day = clock / TICKS_PER_DAY
    }
}

internal fun MinecraftServer.changeTime(request: SetTimeRequest) {
    val command = when (request.valueCase) {
        SetTimeRequest.ValueCase.TIME -> "time set ${request.time}"
        SetTimeRequest.ValueCase.ADD -> "time add ${request.add}"
        SetTimeRequest.ValueCase.PRESET -> "time set ${request.preset}"
        else -> invalidArgument("One of time, add or preset is required")
    }
    if (request.dimension.isBlank()) {
        runCommandOrFail(command)
        return
    }
    val dimension = level(request.dimension).dimension().identifier()
    runCommandOrFail("execute in $dimension run $command")
}

// The visible rain level only fades towards the stored weather state over several ticks.
internal fun MinecraftServer.weatherOf(request: DimensionRef): WeatherInfo {
    val weather = level(request.dimension).weatherData
    return weatherInfo {
        raining = weather.isRaining
        thundering = weather.isThundering
    }
}

internal fun MinecraftServer.changeWeather(request: SetWeatherRequest) {
    val name = when (request.weather) {
        SetWeatherRequest.Weather.WEATHER_RAIN -> "rain"
        SetWeatherRequest.Weather.WEATHER_THUNDER -> "thunder"
        else -> "clear"
    }
    val duration = if (request.duration > 0) " ${request.duration}t" else ""
    runCommandOrFail("weather $name$duration")
}

internal fun MinecraftServer.listGameRules(): GameRules {
    val rules = gameRules
    return gameRules {
        rules.availableRules().forEach { rule -> this.rules[rule.id()] = rules.getAsString(rule) }
    }
}

internal fun MinecraftServer.changeGameRule(request: SetGameRuleRequest) {
    val rule = gameRules.availableRules()
        .filter { it.id() == request.name || it.identifier.toString() == request.name }
        .findFirst()
        .orElse(null) ?: notFound("Unknown game rule: ${request.name}")
    setRule(rule, request.value)
}

private fun <T : Any> MinecraftServer.setRule(rule: GameRule<T>, value: String) {
    val parsed = rule.deserialize(value).getOrThrow {
        invalidArgument("Invalid value for ${rule.id()}: $it")
    }
    gameRules.set(rule, parsed, this)
}

internal fun MinecraftServer.spawnPoint(): SpawnPoint {
    val data = overworld().respawnData
    return spawnPoint {
        dimension = data.dimension().identifier().toString()
        pos = data.pos().toProto()
        yaw = data.yaw()
        pitch = data.pitch()
    }
}

internal fun MinecraftServer.changeSpawnPoint(request: SpawnPoint) {
    level(request.dimension)
    val data = LevelData.RespawnData.of(
        dimensionKey(request.dimension),
        request.pos.toMc(),
        request.yaw,
        request.pitch,
    )
    overworld().respawnData = data
}

internal fun MinecraftServer.explode(request: ExplodeRequest) {
    val default = Level.ExplosionInteraction.TNT
    val interaction = enumByName(request.interaction, default, "explosion interaction")
    val level = level(request.dimension)
    val position = request.position
    level.explode(
        null,
        position.x,
        position.y,
        position.z,
        request.power,
        request.fire,
        interaction,
    )
}
