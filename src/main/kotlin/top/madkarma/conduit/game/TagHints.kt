package top.madkarma.conduit.game

import kotlin.math.abs
import net.minecraft.core.HolderLookup

private const val MAX_SIMILAR_TAGS = 5

/** Fails for a tag that the registry lacks, and names the similar tags it has. */
fun unknownTag(kind: String?, asked: String, registry: HolderLookup.RegistryLookup<*>): Nothing {
    val known = registry.listTagIds().map { it.location().toString() }.toList()
    val similar = similarTags(asked.removePrefix("#"), known)
    val hint = if (similar.isEmpty()) "" else " Similar tags: ${similar.joinToString { "#$it" }}"
    val name = if (kind == null) "tag" else "$kind tag"
    notFound("Unknown $name: $asked.$hint")
}

/**
 * Up to five tags whose path contains the asked path or is part of it, ignoring a plural s, so
 * "minecraft:chests" finds "c:chests". The closest lengths come first.
 */
internal fun similarTags(asked: String, known: List<String>): List<String> {
    val wanted = stem(asked)
    return known
        .filter {
            val path = stem(it)
            path.contains(wanted) || wanted.contains(path)
        }
        .sortedWith(compareBy({ abs(stem(it).length - wanted.length) }, { it }))
        .take(MAX_SIMILAR_TAGS)
}

private fun stem(id: String): String = id.substringAfter(':').lowercase().removeSuffix("s")
