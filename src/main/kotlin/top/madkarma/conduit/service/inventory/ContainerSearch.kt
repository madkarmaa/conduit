package top.madkarma.conduit.service.inventory

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import top.madkarma.conduit.game.*

@Serializable
data class ContainerMatch(
    val pos: BlockCoordinates,
    /** The container's block, such as "minecraft:chest". */
    val block: String,
    /** Matching items, counting those inside shulker boxes and bundles. */
    val count: Int,
)

@Serializable data class ItemSearch(val total: Int, val containers: List<ContainerMatch>)

/**
 * Containers in a cuboid that hold an item id or #tag, nearest to center first. It only reads the
 * block entities of the chunks in the cuboid, so it covers large areas quickly.
 */
internal fun MinecraftServer.findItems(
    dimension: String?,
    item: String,
    from: BlockPos,
    to: BlockPos,
    center: BlockPos,
    loadedOnly: Boolean,
    limit: Int,
): ItemSearch {
    val level = level(dimension)
    val matches = itemFilter(item)
    val found = mutableListOf<Pair<BlockPos, ContainerMatch>>()

    for (chunkPos in ChunkPos.rangeClosed(ChunkPos.containing(from), ChunkPos.containing(to))) {
        if (loadedOnly && !level.hasChunk(chunkPos.x(), chunkPos.z())) continue

        for ((pos, blockEntity) in level.getChunk(chunkPos.x(), chunkPos.z()).blockEntities) {
            if (!inside(pos, from, to) || blockEntity !is Container) continue

            val count =
                (0 until blockEntity.containerSize).sumOf {
                    count(blockEntity.getItem(it), matches)
                }
            if (count == 0) continue

            val block = BuiltInRegistries.BLOCK.getKey(blockEntity.blockState.block).toString()
            found += pos to ContainerMatch(pos.toCoordinates(), block, count)
        }
    }

    val nearest = found.sortedBy { it.first.distSqr(center) }.take(limit).map { it.second }
    return ItemSearch(found.sumOf { it.second.count }, nearest)
}

private fun MinecraftServer.itemFilter(input: String): (ItemStack) -> Boolean {
    if (input.startsWith("#")) {
        val tag = TagKey.create(Registries.ITEM, parseId(input.removePrefix("#")))
        val items = registryAccess().lookupOrThrow(Registries.ITEM)
        if (items.get(tag).isEmpty) unknownTag("item", input, items)
        return { stack -> stack.`is` { it.`is`(tag) } }
    }

    val item =
        BuiltInRegistries.ITEM.getOptional(parseId(input)).getOrNull()
            ?: notFound("Unknown item: $input")
    return { it.item == item }
}

// Shulker boxes and bundles carry their items as a component, which can nest.
private fun count(stack: ItemStack, matches: (ItemStack) -> Boolean): Int {
    if (stack.isEmpty) return 0

    val nested =
        stack.get(DataComponents.CONTAINER)?.itemCopies()?.toList().orEmpty() +
            stack.get(DataComponents.BUNDLE_CONTENTS)?.itemCopies()?.toList().orEmpty()
    val own = if (matches(stack)) stack.count else 0
    return own + nested.sumOf { count(it, matches) }
}

private fun inside(pos: BlockPos, from: BlockPos, to: BlockPos): Boolean =
    pos.x in from.x..to.x && pos.y in from.y..to.y && pos.z in from.z..to.z
