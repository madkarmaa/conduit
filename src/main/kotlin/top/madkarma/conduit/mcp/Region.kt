package top.madkarma.conduit.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.service.world.BlockRegion

// Each palette entry gets one character, and air gets ".", so the shape of a build stands out.
private const val SYMBOLS =
    "#@%&*+=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$!?<>/|~^"
private val AIR = setOf("minecraft:air", "minecraft:cave_air", "minecraft:void_air")

/** Counts each block state, most common first. */
fun summarizeRegion(region: BlockRegion): Reply {
    val counts =
        region.blocks
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { (index, count) -> stateName(region, index) to count }

    val rows = counts.map { (state, count) -> "$count $state" }
    val data =
        dataOf(
            "min" to encoded(region.min),
            "size" to encoded(region.size),
            "counts" to
                JsonObject(counts.associate { (state, count) -> state to JsonPrimitive(count) }),
        )

    return lines(header(region), rows, data)
}

/** Prints every y layer as a grid of characters, with rows along z and columns along x. */
fun drawRegion(region: BlockRegion): Reply {
    val size = region.size
    val symbols =
        region.palette.mapIndexed { index, state ->
            if (baseId(state.serialized) in AIR) '.' else SYMBOLS.getOrElse(index) { '?' }
        }
    val legend =
        region.palette.mapIndexed { index, state -> "${symbols[index]} ${state.serialized}" }

    val layers =
        (0 until size.y).map { y ->
            val rows =
                (0 until size.z).map { z ->
                    val start = (y * size.z + z) * size.x
                    region.blocks
                        .subList(start, start + size.x)
                        .map { symbols.getOrElse(it) { '?' } }
                        .joinToString("")
                }
            "y=${region.min.y + y}\n${rows.joinToString("\n")}"
        }

    val text = listOf(header(region), "Rows run along z, columns along x.") + legend + layers
    return Reply(
        text.joinToString("\n"),
        dataOf("min" to encoded(region.min), "size" to encoded(region.size)),
    )
}

fun paletteFits(region: BlockRegion): Boolean = region.palette.size <= SYMBOLS.length

private fun header(region: BlockRegion): String {
    val min = region.min
    val size = region.size
    return "Region from ${min.x} ${min.y} ${min.z}, size ${size.x}x${size.y}x${size.z} (x, y, z)"
}

private fun stateName(region: BlockRegion, index: Int): String =
    region.palette.getOrNull(index)?.serialized ?: "unknown"

private fun baseId(serialized: String): String = serialized.split('[', '{').first()
