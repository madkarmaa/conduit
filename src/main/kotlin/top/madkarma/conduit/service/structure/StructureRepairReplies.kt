package top.madkarma.conduit.service.structure

import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.Reply
import top.madkarma.conduit.mcp.ToolInput
import top.madkarma.conduit.mcp.dataOf
import top.madkarma.conduit.mcp.done
import top.madkarma.conduit.mcp.encoded
import top.madkarma.conduit.mcp.lines
import top.madkarma.conduit.service.world.blockFilter

private const val GROUP_LIMIT = 20
private const val SAMPLE_POSITIONS = 3

internal fun MinecraftServer.compareReply(input: ToolInput): Reply {
    val found = structureBlocksOf(input)
    val missing = differences(found.blocks.filter { it.missing }, SAMPLE_POSITIONS)
    val different = differences(found.blocks.filter { it.different }, SAMPLE_POSITIONS)
    val missingCount = missing.sumOf { it.count }
    val differentCount = different.sumOf { it.count }
    val matching = found.blocks.size - missingCount - differentCount

    val skipped =
        if (found.skippedPieces == 0) ""
        else ", and skipped ${found.skippedPieces} built without one or fitted to the terrain"
    val header =
        "${found.structure}: compared ${found.pieces - found.skippedPieces} template pieces" +
            "$skipped. Blocks: $matching match, $missingCount missing, $differentCount different."

    return lines(
        header,
        groupLines("Missing", missing) + groupLines("Different", different),
        dataOf(
            "structure" to JsonPrimitive(found.structure),
            "matching" to JsonPrimitive(matching),
            "missing" to encoded(missing),
            "different" to encoded(different),
        ),
    )
}

internal fun MinecraftServer.restoreReply(input: ToolInput): Reply {
    val found = structureBlocksOf(input)
    val includeDifferent = input.boolean("include_different")
    val filters = input.strings("blocks").map(::blockFilter)

    val chosen =
        found.blocks
            .filter { it.missing || (includeDifferent && it.different) }
            .filter { block -> filters.isEmpty() || filters.any { it(block.expected) } }
    if (chosen.isEmpty()) return done("${found.structure} has no blocks to restore")

    restoreBlocks(this, input.stringOrNull("dimension"), chosen)
    val restored = differences(chosen, 0)
    return lines(
        "Restored ${chosen.size} blocks of ${found.structure}:",
        restored.take(GROUP_LIMIT).map { "${it.count} ${it.block}" } +
            listOfNotNull(moreLine(restored)),
        dataOf("restored" to encoded(restored)),
    )
}

private fun MinecraftServer.structureBlocksOf(input: ToolInput): StructureBlocks =
    structureBlocks(
        input.stringOrNull("dimension"),
        input.decode<BlockCoordinates>("pos").toMc(),
        input.stringOrNull("structure"),
    )

private fun groupLines(title: String, groups: List<BlockDifference>): List<String> {
    if (groups.isEmpty()) return emptyList()

    val rows =
        groups.take(GROUP_LIMIT).map { group ->
            val now = group.now?.let { ", now $it," }.orEmpty()
            val positions = group.positions.joinToString(", ") { "${it.x} ${it.y} ${it.z}" }
            "${group.count} ${group.block}$now at $positions"
        }
    return listOf("$title:") + rows + listOfNotNull(moreLine(groups))
}

/** The blocks of the groups past the listed ones, so the listed counts add up to the total. */
internal fun moreLine(groups: List<BlockDifference>): String? {
    val rest = groups.drop(GROUP_LIMIT)
    if (rest.isEmpty()) return null
    return "${rest.sumOf { it.count }} more blocks of ${rest.size} other kinds"
}
