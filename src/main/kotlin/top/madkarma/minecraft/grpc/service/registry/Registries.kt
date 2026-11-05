package top.madkarma.minecraft.grpc.service.registry

import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import top.madkarma.minecraft.grpc.game.parseId
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.notFound
import kotlin.jvm.optionals.getOrNull

private const val DEFAULT_PAGE_SIZE = 1000
private const val MAX_PAGE_SIZE = 10000

internal fun requireId(id: String, kind: String): Identifier {
    if (id.isBlank()) invalidArgument("A $kind id is required")
    return parseId(id)
}

internal fun <T> List<T>.page(offset: Int, limit: Int): List<T> {
    if (offset < 0) invalidArgument("Offset must not be negative")
    if (limit !in 0..MAX_PAGE_SIZE) invalidArgument("Limit must be between 0 and $MAX_PAGE_SIZE")

    return drop(offset).take(if (limit == 0) DEFAULT_PAGE_SIZE else limit)
}

internal fun MinecraftServer.describeRegistries(): RegistryList {
    val lookups = registryAccess().listRegistries().toList() +
        reloadableRegistries().lookup().listRegistries().toList()

    val infos = lookups.map { lookup ->
        registryInfo {
            id = lookup.key().identifier().toString()
            entryCount = lookup.listElementIds().count().toInt()
        }
    }

    return registryList { registries += infos.distinctBy { it.id }.sortedBy { it.id } }
}

internal fun MinecraftServer.readRegistryEntries(
    request: ListRegistryEntriesRequest,
): RegistryEntryList {
    val ids = memberIds(request.registry, request.tag)
        .filter { it.contains(request.filter, ignoreCase = true) }

    return registryEntryList {
        total = ids.size
        entries += ids.page(request.offset, request.limit).map { registryEntry { id = it } }
    }
}

internal fun MinecraftServer.readTags(request: ListTagsRequest): TagList {
    val tags = taggedRegistry(request.registry).getTags().map { named ->
        tag {
            id = named.key().location().toString()
            entries += named.map { it.registeredName }.sorted()
        }
    }

    return tagList { this.tags += tags.toList().sortedBy { it.id } }
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

// The reloadable registries are loaded without tags.
private fun MinecraftServer.taggedRegistry(id: String): Registry<Any> {
    val key = registryKey(id)
    registryAccess().lookup(key).getOrNull()?.let { return it }

    if (reloadableRegistries().lookup().lookup(key).isPresent) {
        notFound("Registry $id has no tags")
    }

    notFound("Unknown registry: $id")
}
