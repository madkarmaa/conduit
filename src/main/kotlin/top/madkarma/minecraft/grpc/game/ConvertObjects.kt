package top.madkarma.minecraft.grpc.game

import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import top.madkarma.minecraft.grpc.proto.blockState
import top.madkarma.minecraft.grpc.proto.entityInfo
import top.madkarma.minecraft.grpc.proto.itemStack
import top.madkarma.minecraft.grpc.proto.statusEffect
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import kotlin.jvm.optionals.getOrNull
import top.madkarma.minecraft.grpc.proto.BlockState as ProtoBlockState
import top.madkarma.minecraft.grpc.proto.EntityInfo as ProtoEntityInfo
import top.madkarma.minecraft.grpc.proto.ItemStack as ProtoItemStack
import top.madkarma.minecraft.grpc.proto.StatusEffect as ProtoStatusEffect

data class ParsedBlock(val state: BlockState, val nbt: CompoundTag?)

fun parseBlock(registries: HolderLookup.Provider, input: String): ParsedBlock {
    val result = BlockStateParser.parseForBlock(
        registries.lookupOrThrow(Registries.BLOCK),
        input,
        true,
    )

    return ParsedBlock(result.blockState(), result.nbt())
}

fun BlockState.toProto(): ProtoBlockState {
    val state = this
    return blockState {
        id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        state.values.forEach { value -> properties[value.property().name] = value.valueName() }
        serialized = BlockStateParser.serialize(state)
    }
}

fun ItemStack.toProto(registries: HolderLookup.Provider): ProtoItemStack {
    if (isEmpty) return itemStack { id = "minecraft:air" }
    val stack = this

    return itemStack {
        id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        count = stack.count
        components = encodeComponents(stack.componentsPatch, registries)
    }
}

fun ProtoItemStack.toMc(registries: HolderLookup.Provider): ItemStack {
    if (id.isBlank() || id == "minecraft:air" || count < 0) return ItemStack.EMPTY

    val item = BuiltInRegistries.ITEM.get(parseId(id)).orElseGet {
        invalidArgument("Unknown item: $id")
    }
    val patch = decodeComponents(components, registries)

    return ItemStack(item, if (count == 0) 1 else count, patch)
}

private fun encodeComponents(patch: DataComponentPatch, registries: HolderLookup.Provider): String {
    if (patch.isEmpty) return ""

    val context = registries.createSerializationContext(NbtOps.INSTANCE)
    return DataComponentPatch.CODEC.encodeStart(context, patch).getOrThrow().toString()
}

private fun decodeComponents(snbt: String, registries: HolderLookup.Provider): DataComponentPatch {
    if (snbt.isBlank()) return DataComponentPatch.EMPTY

    val context = registries.createSerializationContext(NbtOps.INSTANCE)
    return DataComponentPatch.CODEC.parse(context, TagParser.parseCompoundFully(snbt))
        .getOrThrow { invalidArgument("Invalid item components: $it") }
}

fun Entity.toProto(): ProtoEntityInfo {
    val entity = this
    return entityInfo {
        uuid = entity.stringUUID
        networkId = entity.id
        type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
        name = entity.name.string
        entity.customName?.let { customName = it.string }
        dimension = entity.level().dimension().identifier().toString()
        position = entity.position().toProto()
        rotation = rotationOf(entity.yRot, entity.xRot)
        velocity = entity.deltaMovement.toProto()
        onGround = entity.onGround()
        alive = entity.isAlive
        tags += entity.entityTags()

        if (entity is LivingEntity) {
            health = entity.health
            maxHealth = entity.maxHealth
        }

        passengers += entity.passengers.map { it.stringUUID }
        entity.vehicle?.let { vehicle = it.stringUUID }
    }
}

fun MobEffectInstance.toProto(): ProtoStatusEffect {
    val effect = this
    return statusEffect {
        id = effect.effect.unwrapKey().getOrNull()?.identifier()?.toString() ?: ""
        duration = effect.duration
        amplifier = effect.amplifier
        ambient = effect.isAmbient
        visible = effect.isVisible
        showIcon = effect.showIcon()
    }
}
