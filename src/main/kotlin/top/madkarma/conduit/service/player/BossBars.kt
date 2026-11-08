package top.madkarma.conduit.service.player

import net.minecraft.server.MinecraftServer
import net.minecraft.world.BossEvent
import top.madkarma.conduit.game.*

/** Fields left null stay as they are. Players replaces who sees the bar. */
class BossBarChange(
    val id: String,
    val name: String? = null,
    val color: String? = null,
    val style: String? = null,
    val value: Int? = null,
    val max: Int? = null,
    val visible: Boolean? = null,
    val players: List<String>? = null,
)

/** Creates a boss bar or changes one, like /bossbar. Returns whether it created the bar. */
internal fun MinecraftServer.updateBossBar(change: BossBarChange): Boolean {
    val id = parseId(change.id)
    val registries = registryAccess()
    val existing = customBossEvents.get(id)
    val bar =
        existing
            ?: customBossEvents.create(
                overworld().random,
                id,
                textComponent(change.name ?: change.id, registries),
            )

    change.name?.let { bar.name = textComponent(it, registries) }
    change.color?.let { bar.color = enumByName(it, BossEvent.BossBarColor.WHITE, "boss bar color") }
    change.style?.let {
        bar.overlay = enumByName(it, BossEvent.BossBarOverlay.PROGRESS, "boss bar style")
    }
    change.max?.let { bar.setMax(it) }
    change.value?.let { bar.setValue(it) }
    change.visible?.let { bar.isVisible = it }
    change.players?.let { names -> bar.setPlayers(names.map { player(it) }) }

    return existing == null
}

internal fun MinecraftServer.removeBossBar(id: String) {
    val bar = customBossEvents.get(parseId(id)) ?: notFound("Unknown boss bar: $id")
    bar.removeAllPlayers()
    customBossEvents.remove(bar)
}
