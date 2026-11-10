package top.madkarma.conduit.service.structure

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.StructureStart
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.level
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.Reply
import top.madkarma.conduit.mcp.ToolInput
import top.madkarma.conduit.mcp.dataOf
import top.madkarma.conduit.mcp.done
import top.madkarma.conduit.mcp.encoded
import top.madkarma.conduit.mcp.lines
import top.madkarma.conduit.service.world.Bounds
import top.madkarma.conduit.service.world.boundsOf

/** A generated structure whose box holds a position, and its piece there if it has one. */
@Serializable data class StructureHere(val id: String, val box: Bounds, val piece: PieceHere?)

@Serializable
data class PieceHere(
    /** The template, or the piece type for pieces that the game builds without one. */
    val template: String?,
    val type: String,
    val origin: BlockCoordinates,
    /** As the rotation and mirror inputs of place name them. */
    val rotation: String,
    val mirror: String,
    val box: Bounds,
)

/** The generated structures at the position, the ones with a piece there first. */
internal fun MinecraftServer.structuresAt(dimension: String?, pos: BlockPos): List<StructureHere> {
    val level = level(dimension)
    return startsAt(level, pos)
        .map { (id, start) ->
            StructureHere(id, boundsOf(start.boundingBox), pieceHere(level, start, pos))
        }
        .sortedBy { it.piece == null }
}

/**
 * The starts of the structures at the position, by structure id. Some structures, such as igloos
 * and shipwrecks, move to the ground only when the game builds them, and their saved boxes keep the
 * height they had before. So a box only has to hold the position's column.
 */
internal fun startsAt(level: ServerLevel, pos: BlockPos): List<Pair<String, StructureStart>> {
    val manager = level.structureManager()
    val registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
    val chunk = ChunkPos.containing(pos)

    return manager.getAllStructuresAt(pos).keys.flatMap { structure ->
        val id = registry.getKey(structure).toString()
        manager
            .startsForStructure(chunk.x, chunk.z, structure)
            .filter { it.isValid && it.boundingBox.holdsColumn(pos) }
            .map { id to it }
    }
}

/** The piece at the position, or else the one whose column holds it. */
internal fun pieceAt(start: StructureStart, pos: BlockPos): StructurePiece? =
    start.pieces.firstOrNull { it.boundingBox.isInside(pos) }
        ?: start.pieces.firstOrNull { it.boundingBox.holdsColumn(pos) }

private fun MinecraftServer.pieceHere(
    level: ServerLevel,
    start: StructureStart,
    pos: BlockPos,
): PieceHere? {
    val piece = pieceAt(start, pos) ?: return null
    val template = piece.templatePiece()
    val shift = template?.let { placedPiece(level, it)?.shift } ?: 0

    return PieceHere(
        template?.template?.toString(),
        BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.type).toString(),
        (template?.position ?: piece.boundingBox.let { BlockPos(it.minX(), it.minY(), it.minZ()) })
            .above(shift)
            .toCoordinates(),
        lowercase(template?.settings?.rotation ?: piece.rotation),
        lowercase(template?.settings?.mirror ?: piece.mirror),
        boundsOf(piece.boundingBox.moved(0, shift, 0)),
    )
}

private fun BoundingBox.holdsColumn(pos: BlockPos): Boolean =
    pos.x in minX()..maxX() && pos.z in minZ()..maxZ()

// Pieces without a direction have no rotation or mirror.
private fun lowercase(value: Enum<*>?): String = value?.name?.lowercase() ?: "none"

private fun BlockPos.toCoordinates() = BlockCoordinates(x, y, z)

internal fun MinecraftServer.structuresAtReply(input: ToolInput): Reply {
    val pos = input.decode<BlockCoordinates>("pos")
    val found = structuresAt(input.stringOrNull("dimension"), pos.toMc())
    if (found.isEmpty()) return done("No generated structure at ${text(pos)}")

    return lines(
        "Generated structures at ${text(pos)}, the ones with a piece there first:",
        found.map(::describeHere),
        dataOf("structures" to encoded(found)),
    )
}

private fun describeHere(here: StructureHere): String {
    val structure = "${here.id} spans ${text(here.box.from)} to ${text(here.box.to)}"
    val piece = here.piece ?: return "$structure, with no piece at this position"
    val mirror = if (piece.mirror == "none") "" else ", mirrored ${piece.mirror}"
    return "$structure; piece ${piece.template ?: piece.type} at ${text(piece.origin)}, " +
        "rotated ${piece.rotation}$mirror, spans ${text(piece.box.from)} to ${text(piece.box.to)}"
}

private fun text(pos: BlockCoordinates) = "${pos.x} ${pos.y} ${pos.z}"
