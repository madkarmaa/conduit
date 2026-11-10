package top.madkarma.conduit.service.world

import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.mcp.Reply
import top.madkarma.conduit.mcp.ToolInput
import top.madkarma.conduit.mcp.dataOf
import top.madkarma.conduit.mcp.done
import top.madkarma.conduit.mcp.encoded
import top.madkarma.conduit.mcp.lines

// Some mods keep all their data in one file, which can grow past what fits in a reply.
internal const val MAX_SAVED_DATA_CHARS = 20_000

internal fun MinecraftServer.savedDataReply(input: ToolInput): Reply {
    val dimension = input.stringOrNull("dimension")
    val id = input.stringOrNull("id")
    if (id == null) {
        val ids = savedDataIds(dimension)
        return lines("${ids.size} saved data ids", ids, dataOf("ids" to encoded(ids)))
    }
    return done(capSavedData(savedDataNbt(dimension, id, input.stringOrNull("path"))))
}

/** The SNBT as it is, or its start with the top-level keys to read the rest by path. */
internal fun capSavedData(snbt: String): String {
    if (snbt.length <= MAX_SAVED_DATA_CHARS) return snbt
    val keys = runCatching {
        TagParser.parseCompoundFully(snbt)
    }
        .getOrNull()
        ?.keySet()
        ?.sorted()
        ?.joinToString(", ")
    val hint = keys?.let { " The top-level keys are $it." }.orEmpty()
    return snbt.take(MAX_SAVED_DATA_CHARS) +
        "\n… ${snbt.length - MAX_SAVED_DATA_CHARS} more characters. Read part of it with path.$hint"
}
