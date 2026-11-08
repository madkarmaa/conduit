package top.madkarma.conduit.service.world

import kotlin.math.ceil
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.storage.LevelData
import top.madkarma.conduit.game.*

// Time and weather changes go through the vanilla commands, which keep world clocks and clients
// in sync.

private const val TICKS_PER_DAY = 24000

@Serializable data class TimeInfo(val gameTime: Long, val dayTime: Long, val day: Long)

@Serializable data class WeatherInfo(val raining: Boolean, val thundering: Boolean)

@Serializable
data class WorldInfo(
    val time: TimeInfo,
    val weather: WeatherInfo,
    val spawn: SpawnPoint,
    val worldBorder: WorldBorderInfo,
)

enum class Weather(val command: String) {
    CLEAR("clear"),
    RAIN("rain"),
    THUNDER("thunder"),
}

internal fun MinecraftServer.describeWorld(dimension: String?): WorldInfo =
    WorldInfo(timeOf(dimension), weatherOf(dimension), spawnPoint(), worldBorderOf(dimension))

private fun MinecraftServer.timeOf(dimension: String?): TimeInfo {
    val level = level(dimension)
    val clock = level.defaultClockTime

    return TimeInfo(
        gameTime = level.gameTime,
        dayTime = clock % TICKS_PER_DAY,
        day = clock / TICKS_PER_DAY,
    )
}

/** Sets the time of day to `time`, adds `add` to it, or sets a named preset such as "noon". */
internal fun MinecraftServer.changeTime(
    dimension: String?,
    time: Long?,
    add: Long?,
    preset: String?,
) {
    if (listOfNotNull(time, add, preset).size != 1) {
        invalidInput("Set exactly one of time, add or preset")
    }

    val command =
        when {
            time != null -> "time set $time"
            add != null -> "time add $add"
            else -> "time set $preset"
        }

    if (dimension.isNullOrBlank()) {
        runCommandOrFail(command)
        return
    }

    val id = level(dimension).dimension().identifier()
    runCommandOrFail("execute in $id run $command")
}

// The visible rain level only fades towards the stored weather state over several ticks.
private fun MinecraftServer.weatherOf(dimension: String?): WeatherInfo {
    val weather = level(dimension).weatherData
    return WeatherInfo(raining = weather.isRaining, thundering = weather.isThundering)
}

/** A duration of 0 ticks picks a random duration. */
internal fun MinecraftServer.changeWeather(weather: Weather, durationTicks: Int) {
    val duration = if (durationTicks > 0) " ${durationTicks}t" else ""
    runCommandOrFail("weather ${weather.command}$duration")
}

internal fun MinecraftServer.listGameRules(): Map<String, String> {
    val rules = gameRules
    return rules.availableRules().toList().associate { it.id() to rules.getAsString(it) }
}

internal fun MinecraftServer.changeGameRule(name: String, value: String) {
    val rule =
        gameRules
            .availableRules()
            .filter { it.id() == name || it.identifier.toString() == name }
            .findFirst()
            .orElse(null) ?: notFound("Unknown game rule: $name")

    setRule(rule, value)
}

private fun <T : Any> MinecraftServer.setRule(rule: GameRule<T>, value: String) {
    val parsed =
        rule.deserialize(value).getOrThrow { invalidInput("Invalid value for ${rule.id()}: $it") }

    gameRules.set(rule, parsed, this)
}

private fun MinecraftServer.spawnPoint(): SpawnPoint = overworld().respawnData.toSpawnPoint()

internal fun MinecraftServer.changeSpawnPoint(
    dimension: String?,
    pos: BlockCoordinates,
    yaw: Float,
    pitch: Float,
) {
    level(dimension)
    overworld().respawnData =
        LevelData.RespawnData.of(dimensionKey(dimension), pos.toMc(), yaw, pitch)
}

/** Interaction is NONE, BLOCK, MOB, TNT or TRIGGER, and decides what the explosion breaks. */
internal fun MinecraftServer.explode(
    dimension: String?,
    position: Vector3,
    power: Float,
    fire: Boolean,
    interaction: String?,
) {
    val default = Level.ExplosionInteraction.TNT
    val chosen = enumByName(interaction, default, "explosion interaction")
    val level = level(dimension)

    EditJournal.beforeBox(level, explosionReach(position, power))
    level.explode(null, position.x, position.y, position.z, power, fire, chosen)
}

// An explosion's rays reach at most about 1.75 blocks per point of power.
private fun explosionReach(position: Vector3, power: Float): BoundingBox {
    val reach = ceil(power * 1.75).toInt() + 1
    val center = BlockPos.containing(position.x, position.y, position.z)
    return BoundingBox(center).inflatedBy(reach)
}
