package top.madkarma.minecraft.grpc.service.registry

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
    val infos = registryAccess().registries().map { entry ->
        registryInfo {
            id = entry.key().identifier().toString()
            entryCount = entry.value().size()
        }
    }

    return registryList { registries += infos.toList().sortedBy { it.id } }
}

internal fun MinecraftServer.readRegistryEntries(
    request: ListRegistryEntriesRequest,
): RegistryEntryList {
    val ids = registry(request.registry).memberIds(request.tag)
        .filter { it.contains(request.filter, ignoreCase = true) }

    return registryEntryList {
        total = ids.size
        entries += ids.page(request.offset, request.limit).map { registryEntry { id = it } }
    }
}

internal fun MinecraftServer.readTags(request: ListTagsRequest): TagList {
    val tags = registry(request.registry).getTags().map { named ->
        tag {
            id = named.key().location().toString()
            entries += named.map { it.registeredName }.sorted()
        }
    }

    return tagList { this.tags += tags.toList().sortedBy { it.id } }
}

private fun MinecraftServer.registry(id: String): Registry<Any> {
    val key = ResourceKey.createRegistryKey<Any>(requireId(id, "registry"))
    return registryAccess().lookup(key).getOrNull() ?: notFound("Unknown registry: $id")
}

private fun Registry<Any>.memberIds(tag: String): List<String> {
    if (tag.isBlank()) return keySet().map { it.toString() }.sorted()

    val tagKey = TagKey.create(key(), requireId(tag.removePrefix("#"), "tag"))
    val members = get(tagKey).getOrNull() ?: notFound("Unknown tag: $tag")

    return members.map { it.registeredName }.sorted()
}
