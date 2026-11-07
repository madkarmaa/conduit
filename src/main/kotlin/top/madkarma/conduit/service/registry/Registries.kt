package top.madkarma.conduit.service.registry

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import top.madkarma.conduit.game.*

private const val DEFAULT_PAGE_SIZE = 1000
private const val MAX_PAGE_SIZE = 10000

@Serializable
data class RegistryInfo(
    /** Registry id, such as "minecraft:block" or "minecraft:worldgen/biome". */
    val id: String,
    val entryCount: Int,
)

@Serializable
data class TagInfo(
    /** Tag id without "#", such as "minecraft:logs". */
    val id: String,
    val entries: List<String>,
)

/** One page of ids, with the number of matching ids before paging. */
@Serializable data class IdPage(val total: Int, val ids: List<String>)

internal fun requireId(id: String, kind: String): Identifier {
    if (id.isBlank()) invalidInput("A $kind id is required")
    return parseId(id)
}

internal fun <T> List<T>.page(offset: Int, limit: Int): List<T> {
    if (offset < 0) invalidInput("Offset must not be negative")
    if (limit !in 0..MAX_PAGE_SIZE) invalidInput("Limit must be between 0 and $MAX_PAGE_SIZE")

    return drop(offset).take(if (limit == 0) DEFAULT_PAGE_SIZE else limit)
}

internal fun MinecraftServer.describeRegistries(): List<RegistryInfo> {
    val lookups =
        registryAccess().listRegistries().toList() +
            reloadableRegistries().lookup().listRegistries().toList()

    val infos = lookups.map { lookup ->
        RegistryInfo(lookup.key().identifier().toString(), lookup.listElementIds().count().toInt())
    }

    return infos.distinctBy { it.id }.sortedBy { it.id }
}

/**
 * Entry ids of a registry in id order, filtered by a case-insensitive substring and optionally by a
 * tag such as "#minecraft:logs".
 */
internal fun MinecraftServer.readRegistryEntries(
    registry: String,
    filter: String,
    tag: String?,
    offset: Int,
    limit: Int,
): IdPage {
    val ids = memberIds(registry, tag.orEmpty()).filter { it.contains(filter, ignoreCase = true) }
    return IdPage(ids.size, ids.page(offset, limit))
}

internal fun MinecraftServer.readTags(registry: String): List<TagInfo> {
    val tags =
        taggedRegistry(registry).getTags().map { named ->
            TagInfo(named.key().location().toString(), named.map { it.registeredName }.sorted())
        }

    return tags.toList().sortedBy { it.id }
}

private fun MinecraftServer.memberIds(registryId: String, tag: String): List<String> {
    if (tag.isBlank()) {
        val keys = anyRegistry(registryId).listElementIds()
        return keys.map { it.identifier().toString() }.toList().sorted()
    }

    val registry = taggedRegistry(registryId)
    val tagKey = TagKey.create(registry.key(), requireId(tag.removePrefix("#"), "tag"))
    val members = registry.get(tagKey).getOrNull() ?: notFound("Unknown tag: $tag")

    return members.map { it.registeredName }.sorted()
}

private fun registryKey(id: String): ResourceKey<Registry<Any>> =
    ResourceKey.createRegistryKey(requireId(id, "registry"))

// Loot tables, predicates and item modifiers are only in the reloadable registries.
private fun MinecraftServer.anyRegistry(id: String): HolderLookup.RegistryLookup<Any> {
    val key = registryKey(id)
    return registryAccess().lookup(key).getOrNull()
        ?: reloadableRegistries().lookup().lookup(key).getOrNull()
        ?: notFound("Unknown registry: $id")
}

// The game loads the reloadable registries without tags.
private fun MinecraftServer.taggedRegistry(id: String): Registry<Any> {
    val key = registryKey(id)
    registryAccess().lookup(key).getOrNull()?.let {
        return it
    }

    if (reloadableRegistries().lookup().lookup(key).isPresent) {
        notFound("Registry $id has no tags")
    }

    notFound("Unknown registry: $id")
}
