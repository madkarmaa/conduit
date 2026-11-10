package top.madkarma.conduit.service.world

import kotlinx.coroutines.future.await
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.notFound

// Below the 30 s that a tool call waits for the game, so the search can answer what it found.
private const val SEARCH_BUDGET_NANOS = 20_000_000_000L

// Strongholds have up to 128 ring positions, checked this many at a time, nearest first.
private const val RING_GROUP = 8

/**
 * The nearest structure of an id or #tag, like /locate, without freezing the server. The search
 * works out the candidate chunks from the placement rules, checks them on the server thread a slice
 * at a time, and generates the chunks it must look into on the chunk workers while the server keeps
 * ticking. After its time budget it gives up and says how far it got.
 */
internal suspend fun locateStructure(
    dimension: String?,
    structure: String,
    center: BlockPos,
): Located {
    val deadline = System.nanoTime() + SEARCH_BUDGET_NANOS
    val search = GameServer.run { server -> server.prepareSearch(dimension, structure, center) }

    var nearest =
        search.ringCandidates.chunked(RING_GROUP).firstNotNullOfOrNull { group ->
            nearestOf(search, check(search, group, deadline))
        }

    var radius = 0
    while (radius <= SEARCH_CELLS && search.spreadPlacements.isNotEmpty()) {
        if (System.nanoTime() > deadline) break
        val found = check(search, search.spreadCandidates(radius), deadline)
        // A nearer ring cannot hold anything once a ring found something, as in /locate.
        if (found.isNotEmpty()) {
            nearest = nearestOf(search, found + listOfNotNull(nearest))
            break
        }
        radius++
    }

    val found = nearest ?: notFound(notFoundMessage(search, structure, radius, deadline))
    // The start chunk knows the structure's pieces. Waiting for it here keeps the server ticking.
    val chunk =
        search.level.chunkSource
            .getChunkFuture(
                SectionPos.blockToSectionCoord(found.pos.x),
                SectionPos.blockToSectionCoord(found.pos.z),
                ChunkStatus.STRUCTURE_STARTS,
                true,
            )
            .await()
            .orElse(null)
    return GameServer.run { describeFound(search, found, chunk) }
}

private fun nearestOf(search: Search, found: List<Found>): Found? = found.minByOrNull {
    search.center.distSqr(it.pos)
}

private fun describeFound(search: Search, found: Found, chunk: ChunkAccess?): Located {
    val start = chunk?.getStartForStructure(found.structure.value())
    val box = start?.takeIf { it.isValid }?.boundingBox
    val pos = box?.center ?: found.pos
    return located(found.structure, pos, search.center, horizontal = true)
        .copy(box = box?.let(::boundsOf))
}

private fun notFoundMessage(
    search: Search,
    structure: String,
    radius: Int,
    deadline: Long,
): String {
    if (search.spreadPlacements.isEmpty()) return "No $structure found"

    val reached = search.blocksAt(radius.coerceAtMost(SEARCH_CELLS))
    if (System.nanoTime() <= deadline) return "No $structure within $reached blocks"

    return "No $structure within $reached blocks after ${SEARCH_BUDGET_NANOS / 1_000_000_000} s of " +
        "searching. It may be farther, and generating new land is slow. Searching again goes " +
        "faster, since the land checked so far is kept."
}
