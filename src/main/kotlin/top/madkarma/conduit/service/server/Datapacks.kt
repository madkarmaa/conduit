package top.madkarma.conduit.service.server

import io.grpc.Status
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.future.await
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.proto.*
import top.madkarma.conduit.rpc.aborted
import top.madkarma.conduit.rpc.failedPrecondition
import top.madkarma.conduit.rpc.notFound

// This is a count because /reload can start while another reload still runs. The API holds one
// extra slot from its check until its own reload ends, so two API calls cannot both start one.
private val reloadsInProgress = AtomicInteger(0)

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

    return datapackList {
        for (pack in packRepository.availablePacks) {
            datapacks += datapack {
                id = pack.id
                title = pack.title.string
                description = pack.description.string
                enabled = pack in selected
                required = isRequired(pack)
                source = sourceOf(pack)
            }
        }
    }
}

internal suspend fun changeDatapackEnabled(request: SetDatapackEnabledRequest): DatapackList {
    if (!reloadsInProgress.compareAndSet(0, 1)) {
        aborted("A datapack reload is already in progress")
    }

    try {
        val selection = GameServer.run { it.selectionWith(request) }
        if (selection != null) reload(GameServer.require(), selection)
    } finally {
        reloadsInProgress.decrementAndGet()
    }

    return GameServer.run { it.listDatapacks() }
}

// Called off the server thread on purpose, because on the server thread reloadResources blocks
// the tick until the reload is done. Every step of the reload still runs on the server thread or
// the worker pool.
private suspend fun reload(server: MinecraftServer, selection: List<String>) {
    try {
        server.reloadResources(selection).await()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        throw Status.INTERNAL.withDescription("Datapack reload failed: $exception")
            .withCause(exception)
            .asException()
    }
}

/** Returns the selected pack ids after the change, or null when nothing changes. */
private fun MinecraftServer.selectionWith(request: SetDatapackEnabledRequest): List<String>? {
    packRepository.reload()

    val pack = packRepository.getPack(request.id) ?: notFound("Unknown datapack: ${request.id}")
    val selected = packRepository.selectedPacks.toMutableList()

    if ((pack in selected) == request.enabled) return null

    if (request.enabled) {
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

private fun sourceOf(pack: Pack): Datapack.Source =
    when (pack.packSource) {
        PackSource.BUILT_IN -> Datapack.Source.SOURCE_BUILT_IN
        PackSource.FEATURE -> Datapack.Source.SOURCE_FEATURE
        PackSource.WORLD -> Datapack.Source.SOURCE_WORLD
        PackSource.SERVER -> Datapack.Source.SOURCE_SERVER
        else -> Datapack.Source.SOURCE_OTHER
    }
