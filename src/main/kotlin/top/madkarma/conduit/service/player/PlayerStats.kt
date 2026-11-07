package top.madkarma.conduit.service.player

import java.nio.file.Files
import java.util.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.stats.ServerStatsCounter
import net.minecraft.stats.StatType
import net.minecraft.stats.StatsCounter
import net.minecraft.world.level.storage.LevelResource
import top.madkarma.conduit.game.notFound

/**
 * Statistics of an online or offline player, as "<stat type>:<stat id>" to value, such as
 * "minecraft:mined:minecraft:stone". Lists only the statistics that are not zero.
 */
internal fun MinecraftServer.playerStats(ref: String): Map<String, Int> {
    val id = resolvePlayerId(ref)
    val counter = playerList.getPlayer(id)?.stats ?: savedStats(id)
    val values = mutableMapOf<String, Int>()

    for (type in BuiltInRegistries.STAT_TYPE) collectStats(type, counter, values)
    return values
}

// This reads the file the same way the game does at login. It never saves the counter, so it
// cannot change the file.
private fun MinecraftServer.savedStats(id: UUID): StatsCounter {
    val file = getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve("$id.json")
    if (!Files.isRegularFile(file)) notFound("No saved statistics for player $id")

    return ServerStatsCounter(this, file)
}

// Reading a counter registers every stat it holds a value for in that stat's type.
private fun <T : Any> collectStats(
    type: StatType<T>,
    counter: StatsCounter,
    values: MutableMap<String, Int>,
) {
    val typeId = BuiltInRegistries.STAT_TYPE.getKey(type)

    for (stat in type) {
        val value = counter.getValue(stat)
        if (value != 0) values["$typeId:${type.registry.getKey(stat.value)}"] = value
    }
}
