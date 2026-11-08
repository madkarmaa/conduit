package top.madkarma.conduit.service.inventory

import java.util.UUID
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.npc.InventoryCarrier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.SlotRanges
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.*

private const val MOB_INVENTORY = "mob.inventory."

// The slots of an entity's own storage, which give fills. The others are equipment and the like.
private val STORAGE_PREFIXES = listOf("container.", "horse.", MOB_INVENTORY)

/** A slot by the name the /item command uses, such as "container.3" or "armor.head". */
private class NamedSlot(val name: String, val access: SlotAccess)

@Serializable data class EntitySlot(val slot: String, val item: ItemInfo)

/**
 * The slots of the entity's items: the equipment of any living entity, such as an armor stand's
 * armor or a copper golem's held item, and the storage of chest minecarts and boats, donkeys and
 * llamas, villagers, pillagers, piglins and allays, and an item frame's contents. Players have
 * their own tools, which keep the limits of chat mode.
 */
private fun MinecraftServer.slotsOf(uuid: String): List<NamedSlot> {
    val entity = entity(uuid)
    if (entity is Player) invalidInput("Use player for players")
    return entity.namedSlots()
}

private fun Entity.namedSlots(): List<NamedSlot> {
    val slots =
        SlotRanges.singleSlotNames().toList().mapNotNull { name ->
            val id = SlotRanges.nameToIds(name)?.slots()?.firstOrNull() ?: return@mapNotNull null
            getSlot(id)?.let { NamedSlot(name, it) }
        }

    // Allays carry an inventory that the game gives no slot names, unlike villagers or pillagers.
    if (this !is InventoryCarrier || slots.any { it.name.startsWith(MOB_INVENTORY) }) return slots
    val inventory = this.inventory
    return slots +
        (0 until inventory.containerSize).map { index ->
            NamedSlot(
                "$MOB_INVENTORY$index",
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
        slots.firstOrNull { it.name == slotName }
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
        if (slot.access.get().isEmpty && slot.access.set(stack.copy())) stack.count = 0
    }

    return stack.count
}

// Undo puts the entity's items back.
private fun MinecraftServer.slotsBeforeEdit(uuid: String): List<NamedSlot> {
    val slots = slotsOf(uuid)
    val entity = entity(uuid)
    EditJournal.before { EntityItems(entity.uuid, slots.map { it.name to it.access.get().copy() }) }
    return slots
}

/** An entity's items before a recorded call changed them, by slot name. */
private class EntityItems(private val id: UUID, private val items: List<Pair<String, ItemStack>>) :
    UndoableChange {
    override fun changed(): Boolean {
        val entity = GameServer.current?.findEntity(id) ?: return true
        val now = entity.namedSlots().associate { it.name to it.access.get() }
        return items.any { (name, stack) -> !ItemStack.matches(stack, now[name] ?: stack) }
    }

    override fun overlaps(other: UndoableChange): Boolean = other is EntityItems && other.id == id

    override fun undo(server: MinecraftServer) {
        val slots = server.findEntity(id)?.namedSlots()?.associateBy { it.name } ?: return
        items.forEach { (name, stack) -> slots[name]?.access?.set(stack.copy()) }
    }
}

private fun MinecraftServer.findEntity(id: UUID): Entity? = allLevels.firstNotNullOfOrNull {
    it.getEntity(id)
}
