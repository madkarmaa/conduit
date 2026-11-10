package top.madkarma.conduit.service.structure

import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.mcp.Reply
import top.madkarma.conduit.mcp.ToolInput
import top.madkarma.conduit.mcp.dataOf
import top.madkarma.conduit.mcp.encoded
import top.madkarma.conduit.mcp.lines

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
    val more = groups.size - GROUP_LIMIT
    return listOf("$title:") + rows + listOfNotNull("$more more blocks".takeIf { more > 0 })
}
