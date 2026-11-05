package top.madkarma.minecraft.grpc.client.service

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import top.madkarma.minecraft.grpc.proto.KeyList
import top.madkarma.minecraft.grpc.proto.keyInfo
import top.madkarma.minecraft.grpc.proto.keyList
import top.madkarma.minecraft.grpc.rpc.notFound

// The game resets key states from the (absent) physical keyboard, so held keys are re-applied
// at the start of every client tick.
object KeyInput {
    // Ticks left until release, or null while held until released explicitly.
    private val held = HashMap<KeyMapping, Int?>()

    fun mapping(name: String): KeyMapping =
        Minecraft.getInstance().options.keyMappings.firstOrNull { it.name == name }
            ?: notFound("Unknown key mapping: $name")

    fun set(mapping: KeyMapping, down: Boolean) {
        if (down) held[mapping] = null else held -= mapping
        mapping.isDown = down
    }

    fun press(mapping: KeyMapping, ticks: Int) {
        click(mapping)
        held[mapping] = maxOf(ticks, 1)
        mapping.isDown = true
    }

    fun click(mapping: KeyMapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()))
    }

    fun describe(client: Minecraft): KeyList = keyList {
        for (mapping in client.options.keyMappings) {
            keys += keyInfo {
                name = mapping.name
                category = mapping.category.id().toString()
                boundTo = mapping.saveString()
                down = mapping.isDown
            }
        }
    }

    fun releaseAll() {
        held.clear()
        KeyMapping.releaseAll()
    }

    fun startTick() {
        for (mapping in held.keys) mapping.isDown = true
    }

    fun endTick() {
        val iterator = held.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val ticksLeft = entry.value ?: continue
            if (ticksLeft > 1) {
                entry.setValue(ticksLeft - 1)
                continue
            }
            entry.key.isDown = false
            iterator.remove()
        }
    }
}
