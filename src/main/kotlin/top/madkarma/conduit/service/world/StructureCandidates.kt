package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.notFound

// The placement math of the structure search, which needs no chunks.

// As far as /locate looks, in cells of each placement's spacing.
internal const val SEARCH_CELLS = 100

/** A chunk where one of the wanted structures may start, by the placement math alone. */
internal class Candidate(
    val chunk: ChunkPos,
    val placement: StructurePlacement,
    val structures: Set<Holder<Structure>>,
)

/** What the search needs from the world, read once on the server thread. */
internal class Search(
    val level: ServerLevel,
    val center: BlockPos,
    val seed: Long,
    /** The candidates of concentric placements, such as strongholds, nearest first. */
    val ringCandidates: List<Candidate>,
    val spreadPlacements: Map<RandomSpreadStructurePlacement, Set<Holder<Structure>>>,
) {
    private val originX = SectionPos.blockToSectionCoord(center.x)
    private val originZ = SectionPos.blockToSectionCoord(center.z)

    /** The candidates of the random spread placements on the edge of the square of a radius. */
    fun spreadCandidates(radius: Int): List<Candidate> =
        spreadPlacements.flatMap { (placement, structures) ->
            squareEdge(radius).map { (cellX, cellZ) ->
                val sectorX = originX + placement.spacing() * cellX
                val sectorZ = originZ + placement.spacing() * cellZ
                Candidate(
                    placement.getPotentialStructureChunk(seed, sectorX, sectorZ),
                    placement,
                    structures,
                )
            }
        }

    /** How far a radius reaches, in blocks, for the widest spacing. */
    fun blocksAt(radius: Int): Int =
        radius * (spreadPlacements.keys.maxOfOrNull { it.spacing() } ?: 1) * SectionPos.SECTION_SIZE
}

internal fun MinecraftServer.prepareSearch(
    dimension: String?,
    structure: String,
    center: BlockPos,
): Search {
    val level = level(dimension)
    if (!worldGenSettings.options().generateStructures())
        notFound("This world generates no structures")

    val state = level.chunkSource.generatorState
    val placements = mutableMapOf<StructurePlacement, MutableSet<Holder<Structure>>>()
    for (holder in holders(Registries.STRUCTURE, structure, "structure")) {
        state.getPlacementsForStructure(holder).forEach {
            placements.getOrPut(it) { mutableSetOf() } += holder
        }
    }
    if (placements.isEmpty())
        notFound("$structure does not generate in ${level.dimension().identifier()}")

    val ringCandidates =
        placements
            .filterKeys { it is ConcentricRingsStructurePlacement }
            .flatMap { (placement, structures) ->
                state
                    .getRingPositionsFor(placement as ConcentricRingsStructurePlacement)
                    .orEmpty()
                    .map {
                        Candidate(it, placement, structures)
                    }
            }
            .sortedBy { center.distSqr(it.placement.getLocatePos(it.chunk)) }

    @Suppress("UNCHECKED_CAST")
    val spread =
        placements.filterKeys { it is RandomSpreadStructurePlacement }
            as Map<RandomSpreadStructurePlacement, Set<Holder<Structure>>>

    return Search(level, center, state.levelSeed, ringCandidates, spread)
}

/** The cells on the edge of a square of the radius around the origin cell. */
private fun squareEdge(radius: Int): List<Pair<Int, Int>> =
    (-radius..radius).flatMap { cellX ->
        (-radius..radius).mapNotNull { cellZ ->
            val edge = cellX == -radius || cellX == radius || cellZ == -radius || cellZ == radius
            (cellX to cellZ).takeIf { edge }
        }
    }
