package top.madkarma.conduit.service.inventory

import java.util.UUID
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.UndoableChange

/** An entity's items before a recorded call changed them, by slot name. */
internal class EntityItems(private val id: UUID, private val items: List<Pair<String, ItemStack>>) :
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

internal fun MinecraftServer.findEntity(id: UUID): Entity? = allLevels.firstNotNullOfOrNull {
    it.getEntity(id)
}
