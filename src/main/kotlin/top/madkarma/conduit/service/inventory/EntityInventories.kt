package top.madkarma.conduit.service.inventory

import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.npc.InventoryCarrier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.SlotRanges
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.*
import top.madkarma.conduit.service.entity.editedEntity

private const val MOB_INVENTORY = "mob.inventory."

// The slots of an entity's own storage, which give fills. The others are equipment and the like.
private val STORAGE_PREFIXES = listOf("container.", "horse.", MOB_INVENTORY)

// The /item command has several names for some slots, such as "container.0" and "hotbar.0". A
// slot shows under the first of these kinds of names that it has.
private val PREFERRED_PREFIXES = STORAGE_PREFIXES + listOf("armor.", "weapon.", "saddle")

/**
 * A slot by the name the /item command uses, such as "container.3" or "armor.head". Other names of
 * the same slot work too.
 */
internal class NamedSlot(val names: List<String>, val access: SlotAccess) {
    val name: String = names.minBy { name ->
        PREFERRED_PREFIXES.indexOfFirst { name.startsWith(it) }.takeIf { it >= 0 }
            ?: PREFERRED_PREFIXES.size
    }
}

@Serializable data class EntitySlot(val slot: String, val item: ItemInfo)

/**
 * The slots of the entity's items: the equipment of any living entity, such as an armor stand's
 * armor or a copper golem's held item, and the storage of chest minecarts and boats, donkeys and
 * llamas, villagers, pillagers, piglins and allays, and an item frame's item. Players have their
 * own tools, which keep the limits of chat mode.
 */
private fun MinecraftServer.slotsOf(uuid: String): List<NamedSlot> {
    val entity = entity(uuid)
    if (entity is Player) invalidInput("Use player for players")
    return entity.namedSlots()
}

internal fun Entity.namedSlots(): List<NamedSlot> {
    val namesById =
        SlotRanges.singleSlotNames().toList().groupBy { name ->
            SlotRanges.nameToIds(name)?.slots()?.firstOrNull()
        }
    val slots = namesById.mapNotNull { (id, names) ->
        id?.let(::getSlot)?.let { NamedSlot(names, it) }
    }

    // Allays carry an inventory that the game gives no slot names, unlike villagers or pillagers.
    if (this !is InventoryCarrier || slots.any { it.name.startsWith(MOB_INVENTORY) }) return slots
    val inventory = this.inventory
    return slots +
        (0 until inventory.containerSize).map { index ->
            NamedSlot(
                listOf("$MOB_INVENTORY$index"),
                SlotAccess.of({ inventory.getItem(index) }, { inventory.setItem(index, it) }),
            )
        }
}

internal fun MinecraftServer.entityItems(uuid: String): List<EntitySlot> {
    val registries = registryAccess()
    return slotsOf(uuid).mapNotNull { slot ->
        val stack = slot.access.get()
        if (stack.isEmpty) null else EntitySlot(slot.name, stack.toInfo(registries))
    }
}

/** A null item empties the slot. */
internal fun MinecraftServer.changeEntitySlot(uuid: String, slotName: String, item: ItemInfo?) {
    val slots = slotsBeforeEdit(uuid)
    val slot =
        slots.firstOrNull { slotName in it.names }
            ?: invalidInput(
                "Entity has no slot $slotName. It has ${slots.joinToString(", ") { it.name }}",
            )

    val stack = item?.toMc(registryAccess()) ?: ItemStack.EMPTY
    if (!slot.access.set(stack)) failedPrecondition("$slotName does not take that item")
}

internal fun MinecraftServer.clearEntityItems(uuid: String) {
    slotsBeforeEdit(uuid).forEach { it.access.set(ItemStack.EMPTY) }
}

/** Adds the stack to the entity's storage slots. Returns how many items did not fit. */
internal fun MinecraftServer.giveEntityItem(uuid: String, item: ItemInfo): Int {
    val storage =
        slotsBeforeEdit(uuid).filter { slot -> STORAGE_PREFIXES.any { slot.name.startsWith(it) } }
    if (storage.isEmpty()) failedPrecondition("Entity has no storage. Use set_slot")

    val stack = item.toMc(registryAccess())
    // Fill matching stacks first, then empty slots, like a hopper.
    for (slot in storage) {
        val held = slot.access.get()
        if (held.isEmpty || !ItemStack.isSameItemSameComponents(held, stack)) continue

        val moved = minOf(stack.count, held.maxStackSize - held.count)
        if (moved > 0 && slot.access.set(held.copyWithCount(held.count + moved))) {
            stack.shrink(moved)
        }
    }
    for (slot in storage) {
        if (stack.isEmpty) break
        val placed = stack.copyWithCount(minOf(stack.count, stack.maxStackSize))
        if (slot.access.get().isEmpty && slot.access.set(placed)) stack.shrink(placed.count)
    }

    return stack.count
}

// Undo puts the entity's items back.
private fun MinecraftServer.slotsBeforeEdit(uuid: String): List<NamedSlot> {
    val slots = slotsOf(uuid)
    editedEntity(uuid)
    return slots
}
