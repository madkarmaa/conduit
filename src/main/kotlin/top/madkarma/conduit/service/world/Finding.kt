package top.madkarma.conduit.service.world

import kotlin.jvm.optionals.getOrNull
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.ai.village.poi.PoiManager
import top.madkarma.conduit.game.*

// The same search ranges as /locate.
private const val STRUCTURE_RADIUS_CHUNKS = 100
private const val BIOME_RADIUS = 6400
private const val BIOME_HORIZONTAL_STEP = 32
private const val BIOME_VERTICAL_STEP = 64
private const val POI_RADIUS = 256

@Serializable data class BlockMatch(val pos: BlockCoordinates, val state: String)

@Serializable data class BlockSearch(val total: Int, val matches: List<BlockMatch>)

@Serializable
data class Located(
    /** The structure, biome or point of interest type found. */
    val id: String,
    /** A structure's y is a guess, since only its chunk is known. */
    val pos: BlockCoordinates,
    /** Blocks from the center, leaving out y for structures. */
    val distance: Int,
)

/** Blocks in an area that match a block id, state or #tag, nearest to the area's center first. */
internal fun MinecraftServer.findBlocks(
    dimension: String?,
    block: String,
    area: BlockArea,
    loadedOnly: Boolean,
    limit: Int,
): BlockSearch {
    val level = level(dimension)
    val matches = blockFilter(block)
    val (min, max) = bounds(area.from, area.to)
    val found = ArrayList<BlockPos>()

    for (pos in BlockPos.betweenClosed(min, max)) {
        if (loadedOnly && !level.isLoaded(pos)) continue
        if (matches(level.getBlockState(pos))) found += pos.immutable()
    }

    val nearest = found.sortedBy { it.distSqr(area.center) }.take(limit)
    return BlockSearch(
        total = found.size,
        matches =
            nearest.map {
                BlockMatch(it.toCoordinates(), level.getBlockState(it).toInfo().serialized)
            },
    )
}

internal fun MinecraftServer.locateStructure(
    dimension: String?,
    structure: String,
    center: BlockPos,
): Located {
    val level = level(dimension)
    val structures = holders(Registries.STRUCTURE, structure, "structure")
    val found =
        level.chunkSource.generator.findNearestMapStructure(
            level,
            structures,
            center,
            STRUCTURE_RADIUS_CHUNKS,
            false,
        ) ?: notFound("No $structure within $STRUCTURE_RADIUS_CHUNKS chunks")

    return located(found.second, found.first, center, horizontal = true)
}

internal fun MinecraftServer.locateBiome(
    dimension: String?,
    biome: String,
    center: BlockPos,
): Located {
    val biomes = holders(Registries.BIOME, biome, "biome")
    val found =
        level(dimension)
            .findClosestBiome3d(
                biomes::contains,
                center,
                BIOME_RADIUS,
                BIOME_HORIZONTAL_STEP,
                BIOME_VERTICAL_STEP,
            ) ?: notFound("No $biome within $BIOME_RADIUS blocks")

    return located(found.second, found.first, center, horizontal = false)
}

/** The game only knows the points of interest of chunks loaded since they were placed. */
internal fun MinecraftServer.locatePoi(dimension: String?, poi: String, center: BlockPos): Located {
    val types = holders(Registries.POINT_OF_INTEREST_TYPE, poi, "point of interest type")
    val found =
        level(dimension)
            .poiManager
            .findClosestWithType(types::contains, center, POI_RADIUS, PoiManager.Occupancy.ANY)
            .getOrNull() ?: notFound("No $poi within $POI_RADIUS blocks of loaded chunks")

    return located(found.first, found.second, center, horizontal = false)
}

// An id names one entry and a #tag a group of them, like the arguments of /locate.
private fun <T : Any> MinecraftServer.holders(
    registry: ResourceKey<out Registry<T>>,
    idOrTag: String,
    kind: String,
): HolderSet<T> {
    val lookup = registryAccess().lookupOrThrow(registry)

    if (idOrTag.startsWith("#")) {
        val tag = TagKey.create(registry, parseId(idOrTag.removePrefix("#")))
        return lookup.get(tag).getOrNull() ?: notFound("Unknown $kind tag: $idOrTag")
    }

    val entry = lookup.get(ResourceKey.create(registry, parseId(idOrTag))).getOrNull()
    return HolderSet.direct(entry ?: notFound("Unknown $kind: $idOrTag"))
}

private fun located(
    holder: Holder<*>,
    pos: BlockPos,
    center: BlockPos,
    horizontal: Boolean,
): Located {
    val dx = (pos.x - center.x).toDouble()
    val dy = if (horizontal) 0.0 else (pos.y - center.y).toDouble()
    val dz = (pos.z - center.z).toDouble()

    return Located(
        id = holder.unwrapKey().getOrNull()?.identifier()?.toString() ?: "?",
        pos = pos.toCoordinates(),
        distance = sqrt(dx * dx + dy * dy + dz * dz).toInt(),
    )
}
