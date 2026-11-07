package top.madkarma.conduit.game

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

@Serializable data class Vector3(val x: Double, val y: Double, val z: Double)

@Serializable data class BlockCoordinates(val x: Int, val y: Int, val z: Int)

/** Degrees, as in Minecraft. Yaw 0 faces south and pitch -90 faces up. */
@Serializable data class Rotation(val yaw: Float, val pitch: Float)

fun Vec3.toVector(): Vector3 = Vector3(x, y, z)

fun Vector3.toMc(): Vec3 = Vec3(x, y, z)

fun Vec3i.toCoordinates(): BlockCoordinates = BlockCoordinates(x, y, z)

fun BlockCoordinates.toMc(): BlockPos = BlockPos(x, y, z)

/** Minecraft stores rotations as (pitch, yaw) vectors. */
fun Rotation.toVec2(): Vec2 = Vec2(pitch, yaw)

fun parseId(id: String): Identifier =
    Identifier.tryParse(id) ?: invalidInput("Invalid identifier: $id")

fun dimensionKey(id: String?): ResourceKey<Level> =
    ResourceKey.create(
        Registries.DIMENSION,
        parseId(id.orEmpty().ifBlank { "minecraft:overworld" }),
    )

/** Plain text, or a JSON text component when it starts with { or [. */
fun textComponent(text: String, registries: HolderLookup.Provider): Component {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return Component.literal(text)

    val json =
        try {
            JsonParser.parseString(text)
        } catch (exception: JsonParseException) {
            invalidInput("Invalid text component: ${exception.message}")
        }

    return ComponentSerialization.CODEC.parse(
            registries.createSerializationContext(JsonOps.INSTANCE),
            json,
        )
        .getOrThrow { invalidInput("Invalid text component: $it") }
}

inline fun <reified E : Enum<E>> enumByName(name: String?, default: E, kind: String): E {
    if (name.isNullOrBlank()) return default

    return enumValues<E>().firstOrNull { it.name == name.uppercase() }
        ?: invalidInput("Unknown $kind: $name")
}
