package top.madkarma.conduit.game

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.GlobalPos
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
import net.minecraft.world.level.storage.LevelData

@Serializable
data class BlockStateInfo(
    /** Block id, such as "minecraft:oak_stairs". */
    val id: String,
    val properties: Map<String, String>,
    /** Full state in command syntax, such as "minecraft:oak_stairs[facing=east,half=bottom]". */
    val serialized: String,
)

@Serializable
data class ItemInfo(
    /** Item id, such as "minecraft:diamond_sword". "minecraft:air" is an empty stack. */
    val id: String,
    val count: Int,
    /** Data component patch as SNBT, such as {"minecraft:custom_name":"Excalibur"}. */
    val components: String = "",
)

@Serializable
data class EffectInfo(
    val id: String,
    val duration: Int,
    val amplifier: Int,
    val ambient: Boolean,
    val visible: Boolean,
    val showIcon: Boolean,
)

@Serializable
data class EntityInfo(
    val uuid: String,
    val networkId: Int,
    val type: String,
    val name: String,
    val customName: String?,
    val dimension: String,
    val position: Vector3,
    val rotation: Rotation,
    val velocity: Vector3,
    val onGround: Boolean,
    val alive: Boolean,
    val tags: List<String>,
    /** Only set for living entities. */
    val health: Float?,
    val maxHealth: Float?,
    val passengers: List<String>,
    val vehicle: String?,
)

/** A block in a dimension, such as where a player last died. */
@Serializable data class DimensionPos(val dimension: String, val pos: BlockCoordinates)

fun GlobalPos.toDimensionPos(): DimensionPos =
    DimensionPos(dimension().identifier().toString(), pos().toCoordinates())

@Serializable
data class SpawnPoint(
    val dimension: String,
    val pos: BlockCoordinates,
    val yaw: Float,
    val pitch: Float,
)

data class ParsedBlock(val state: BlockState, val nbt: CompoundTag?)

fun parseBlock(registries: HolderLookup.Provider, input: String): ParsedBlock {
    val result =
        BlockStateParser.parseForBlock(
            registries.lookupOrThrow(Registries.BLOCK),
            input,
            true,
        )

    return ParsedBlock(result.blockState(), result.nbt())
}

fun BlockState.toInfo(): BlockStateInfo =
    BlockStateInfo(
        id = BuiltInRegistries.BLOCK.getKey(block).toString(),
        properties = values.toList().associate { it.property().name to it.valueName() },
        serialized = BlockStateParser.serialize(this),
    )

fun ItemStack.toInfo(registries: HolderLookup.Provider): ItemInfo {
    if (isEmpty) return ItemInfo("minecraft:air", 0)

    return ItemInfo(
        id = BuiltInRegistries.ITEM.getKey(item).toString(),
        count = count,
        components = encodeComponents(componentsPatch, registries),
    )
}

fun ItemInfo.toMc(registries: HolderLookup.Provider): ItemStack {
    if (id.isBlank() || id == "minecraft:air" || count < 0) return ItemStack.EMPTY

    val item =
        BuiltInRegistries.ITEM.get(parseId(id)).orElseGet { invalidInput("Unknown item: $id") }
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
    return DataComponentPatch.CODEC.parse(context, TagParser.parseCompoundFully(snbt)).getOrThrow {
        invalidInput("Invalid item components: $it")
    }
}

fun Entity.toInfo(): EntityInfo {
    val living = this as? LivingEntity

    return EntityInfo(
        uuid = stringUUID,
        networkId = id,
        type = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
        name = name.string,
        customName = customName?.string,
        dimension = level().dimension().identifier().toString(),
        position = position().toVector(),
        rotation = Rotation(yRot, xRot),
        velocity = deltaMovement.toVector(),
        onGround = onGround(),
        alive = isAlive,
        tags = entityTags().toList(),
        health = living?.health,
        maxHealth = living?.maxHealth,
        passengers = passengers.map { it.stringUUID },
        vehicle = vehicle?.stringUUID,
    )
}

fun MobEffectInstance.toInfo(): EffectInfo =
    EffectInfo(
        id = effect.unwrapKey().getOrNull()?.identifier()?.toString() ?: "",
        duration = duration,
        amplifier = amplifier,
        ambient = isAmbient,
        visible = isVisible,
        showIcon = showIcon(),
    )

fun LevelData.RespawnData.toSpawnPoint(): SpawnPoint =
    SpawnPoint(
        dimension = dimension().identifier().toString(),
        pos = pos().toCoordinates(),
        yaw = yaw(),
        pitch = pitch(),
    )
