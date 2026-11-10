package top.madkarma.conduit.service.world

import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureCheckResult
import top.madkarma.conduit.game.GameServer

// The longest the search holds the server thread at a time, less than half a tick.
private const val SLICE_NANOS = 20_000_000L

internal class Found(val pos: BlockPos, val structure: Holder<Structure>)

/**
 * Checks the candidates on the server thread a slice at a time, then generates the chunks that some
 * of them need, all at once on the chunk workers. Returns what it found before the deadline.
 */
internal suspend fun check(
    search: Search,
    candidates: List<Candidate>,
    deadline: Long,
): List<Found> {
    val found = mutableListOf<Found>()
    val needChunks = mutableListOf<Pair<Candidate, Holder<Structure>>>()

    var next = 0
    while (next < candidates.size) {
        if (System.nanoTime() > deadline) return found
        next = GameServer.run { checkSlice(search, candidates, next, found, needChunks) }
    }
    if (needChunks.isEmpty()) return found

    // Asked from the server thread, the chunk cache waits for the chunk there. Asked from here, it
    // hands the request to the server thread and returns at once.
    val futures = needChunks.map { (candidate, _) ->
        val chunk = candidate.chunk
        search.level.chunkSource.getChunkFuture(
            chunk.x(),
            chunk.z(),
            ChunkStatus.STRUCTURE_STARTS,
            true,
        )
    }
    val left = (deadline - System.nanoTime()).coerceAtLeast(0).nanoseconds
    val chunks = withTimeoutOrNull(left) { futures.map { it.await().orElse(null) } } ?: return found

    GameServer.run {
        needChunks.zip(chunks).forEach { (needed, chunk) ->
            val (candidate, structure) = needed
            val start = chunk?.let {
                search.level.structureManager().getStartForStructure(structure.value(), it)
            }
            if (start != null && start.isValid) {
                found += Found(candidate.placement.getLocatePos(start.chunkPos), structure)
            }
        }
    }
    return found
}

// Runs on the server thread until the slice is used up, and returns the next candidate's index.
private fun checkSlice(
    search: Search,
    candidates: List<Candidate>,
    first: Int,
    found: MutableList<Found>,
    needChunks: MutableList<Pair<Candidate, Holder<Structure>>>,
): Int {
    val sliceEnd = System.nanoTime() + SLICE_NANOS
    var index = first
    val manager = search.level.structureManager()

    while (index < candidates.size && System.nanoTime() < sliceEnd) {
        val candidate = candidates[index++]
        for (structure in candidate.structures) {
            when (
                manager.checkStructurePresence(
                    candidate.chunk,
                    structure.value(),
                    candidate.placement,
                    false,
                )
            ) {
                StructureCheckResult.START_PRESENT ->
                    found += Found(candidate.placement.getLocatePos(candidate.chunk), structure)
                StructureCheckResult.CHUNK_LOAD_NEEDED -> needChunks += candidate to structure
                else -> {}
            }
        }
    }
    return index
}
