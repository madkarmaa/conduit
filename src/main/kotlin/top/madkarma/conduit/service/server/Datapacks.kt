package top.madkarma.conduit.service.server

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.aborted
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.notFound

// This is a count because /reload can start while another reload still runs. The API holds one
// extra slot from its check until its own reload ends, so two API calls cannot both start one.
private val reloadsInProgress = AtomicInteger(0)

@Serializable
data class Datapack(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean,
    /** Built-in or needed by a mod, so it cannot be disabled. */
    val required: Boolean,
    val source: DatapackSource,
)

@Serializable data class DatapackList(val datapacks: List<Datapack>)

@Serializable
enum class DatapackSource {
    /** Shipped with the game, such as "vanilla". */
    @SerialName("built_in") BUILT_IN,
    /** Optional built-in packs for experimental features. */
    @SerialName("feature") FEATURE,
    /** From the world's datapacks folder. */
    @SerialName("world") WORLD,
    @SerialName("server") SERVER,
    /** Any other source, such as mods. */
    @SerialName("other") OTHER,
}

// The events fire for every MinecraftServer.reloadResources call: /reload, /datapack and the API.
internal fun trackDatapackReloads() {
    ServerLifecycleEvents.START_DATA_PACK_RELOAD.register { _, _ ->
        reloadsInProgress.incrementAndGet()
    }
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ ->
        reloadsInProgress.decrementAndGet()
    }
}

internal fun MinecraftServer.listDatapacks(): DatapackList {
    packRepository.reload()
    val selected = packRepository.selectedPacks

    val datapacks =
        packRepository.availablePacks.map { pack ->
            Datapack(
                id = pack.id,
                title = pack.title.string,
                description = pack.description.string,
                enabled = pack in selected,
                required = isRequired(pack),
                source = sourceOf(pack),
            )
        }

    return DatapackList(datapacks)
}

/**
 * Enables or disables a datapack and reloads the server's data, like /datapack. Fails while any
 * datapack reload runs, including /reload and /datapack.
 */
internal suspend fun changeDatapackEnabled(id: String, enabled: Boolean): DatapackList {
    if (!reloadsInProgress.compareAndSet(0, 1)) {
        aborted("A datapack reload is already in progress")
    }

    try {
        val selection = GameServer.run { it.selectionWith(id, enabled) }
        if (selection != null) reload(GameServer.require(), selection)
    } finally {
        reloadsInProgress.decrementAndGet()
    }

    return GameServer.run { it.listDatapacks() }
}

/** Reloads the server's data like /reload, and enables a datapack first when it is off. */
internal suspend fun reloadWithDatapack(id: String): DatapackList {
    if (!reloadsInProgress.compareAndSet(0, 1)) {
        aborted("A datapack reload is already in progress")
    }

    try {
        val selection = GameServer.run {
            it.selectionWith(id, enabled = true) ?: it.packRepository.selectedIds.toList()
        }
        reload(GameServer.require(), selection)
    } finally {
        reloadsInProgress.decrementAndGet()
    }

    return GameServer.run { it.listDatapacks() }
}

// Called off the server thread on purpose, because on the server thread reloadResources blocks
// the tick until the reload is done. Every step of the reload still runs on the server thread or
// the worker pool.
private suspend fun reload(server: MinecraftServer, selection: List<String>) {
    server.reloadResources(selection).await()
}

/** Returns the selected pack ids after the change, or null when nothing changes. */
private fun MinecraftServer.selectionWith(id: String, enabled: Boolean): List<String>? {
    packRepository.reload()

    val pack = packRepository.getPack(id) ?: notFound("Unknown datapack: $id")
    val selected = packRepository.selectedPacks.toMutableList()

    if ((pack in selected) == enabled) return null

    if (enabled) {
        if (!pack.requestedFeatures.isSubsetOf(worldData.enabledFeatures())) {
            failedPrecondition("Datapack ${pack.id} needs features this world does not enable")
        }

        pack.defaultPosition.insert(selected, pack, Pack::selectionConfig, false)
    } else {
        checkCanDisable(pack)
        selected.remove(pack)
    }

    return selected.map { it.id }
}

private fun isRequired(pack: Pack): Boolean =
    pack.isRequired || pack.packSource == PackSource.BUILT_IN

private fun checkCanDisable(pack: Pack) {
    if (isRequired(pack)) failedPrecondition("Datapack ${pack.id} is required")

    // The world's feature flags are fixed, so the game keeps the packs that enable them.
    if (pack.packSource == PackSource.FEATURE && !pack.requestedFeatures.isEmpty) {
        failedPrecondition("Feature datapack ${pack.id} cannot be disabled once enabled")
    }
}

private fun sourceOf(pack: Pack): DatapackSource =
    when (pack.packSource) {
        PackSource.BUILT_IN -> DatapackSource.BUILT_IN
        PackSource.FEATURE -> DatapackSource.FEATURE
        PackSource.WORLD -> DatapackSource.WORLD
        PackSource.SERVER -> DatapackSource.SERVER
        else -> DatapackSource.OTHER
    }
