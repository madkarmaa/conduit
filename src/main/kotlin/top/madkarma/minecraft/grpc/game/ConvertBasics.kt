package top.madkarma.minecraft.grpc.game

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.BlockPos as McBlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3 as McVec3
import top.madkarma.minecraft.grpc.proto.BlockPos as ProtoBlockPos
import top.madkarma.minecraft.grpc.proto.Rotation as ProtoRotation
import top.madkarma.minecraft.grpc.proto.Text
import top.madkarma.minecraft.grpc.proto.Vec3 as ProtoVec3
import top.madkarma.minecraft.grpc.proto.blockPos
import top.madkarma.minecraft.grpc.proto.rotation
import top.madkarma.minecraft.grpc.proto.vec3
import top.madkarma.minecraft.grpc.rpc.invalidArgument

fun McVec3.toProto(): ProtoVec3 = vec3 {
    x = this@toProto.x
    y = this@toProto.y
    z = this@toProto.z
}

fun ProtoVec3.toMc(): McVec3 = McVec3(x, y, z)

fun McBlockPos.toProto(): ProtoBlockPos = blockPos {
    x = this@toProto.x
    y = this@toProto.y
    z = this@toProto.z
}

fun ProtoBlockPos.toMc(): McBlockPos = McBlockPos(x, y, z)

fun rotationOf(yaw: Float, pitch: Float): ProtoRotation = rotation {
    this.yaw = yaw
    this.pitch = pitch
}

/** Minecraft stores rotations as (pitch, yaw) vectors. */
fun ProtoRotation.toVec2(): Vec2 = Vec2(pitch, yaw)

fun parseId(id: String): Identifier =
    Identifier.tryParse(id) ?: invalidArgument("Invalid identifier: $id")

fun dimensionKey(id: String): ResourceKey<Level> =
    ResourceKey.create(Registries.DIMENSION, parseId(id.ifBlank { "minecraft:overworld" }))

fun Text.toComponent(registries: HolderLookup.Provider): Component =
    when (contentCase) {
        Text.ContentCase.JSON ->
            ComponentSerialization.CODEC.parse(
                    registries.createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseString(json),
                )
                .getOrThrow { invalidArgument("Invalid text component: $it") }

        Text.ContentCase.PLAIN -> Component.literal(plain)

        else -> Component.empty()
    }

inline fun <reified E : Enum<E>> enumByName(name: String, default: E, kind: String): E {
    if (name.isBlank()) return default

    return enumValues<E>().firstOrNull { it.name == name.uppercase() }
        ?: invalidArgument("Unknown $kind: $name")
}
