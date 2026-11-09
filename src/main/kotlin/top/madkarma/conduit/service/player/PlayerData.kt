package top.madkarma.conduit.service.player

import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.core.GlobalPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodData
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.*
import top.madkarma.conduit.service.inventory.InventoryContents
import top.madkarma.conduit.service.inventory.contents

// This reads saved player data with the same keys and codecs the game loads players with.

internal val INVENTORY_SLOTS = Inventory.INVENTORY_SIZE + Inventory.EQUIPMENT_SLOT_MAPPING.size
internal const val ENDER_CHEST_SLOTS = 27

@Serializable
data class OfflinePlayerInfo(
    val uuid: String,
    /** Last known name, empty if the server does not know it. */
    val name: String,
    /** Unix milliseconds of the last save, or the current time for online players. */
    val lastPlayed: Long,
    val online: Boolean,
    val dimension: String,
    val position: Vector3,
    val rotation: Rotation,
    val gameMode: String,
    val health: Float,
    val foodLevel: Int,
    val saturation: Float,
    val experienceLevel: Int,
    val experienceProgress: Float,
    val totalExperience: Int,
    val inventory: InventoryContents,
    val enderChest: InventoryContents,
    val effects: List<EffectInfo>,
    /** Bed or respawn anchor, if the player has one. */
    val spawnPoint: SpawnPoint?,
    val lastDeath: DimensionPos? = null,
)

internal fun MinecraftServer.describePlayerData(
    id: UUID,
    data: CompoundTag,
    reporter: ProblemReporter,
): OfflinePlayerInfo {
    val input = TagValueInput.create(reporter, registryAccess(), data)
    val facing = input.read("Rotation", Vec2.CODEC).getOrNull() ?: Vec2.ZERO
    val food = FoodData().apply { readAdditionalSaveData(input) }

    // The game still saves the mode as its numeric id, which only the deprecated codec reads.
    val gameType =
        input.getInt("playerGameType").getOrNull()?.let(GameType::byId) ?: defaultGameType

    val effects =
        input.read("active_effects", MobEffectInstance.CODEC.listOf()).getOrNull().orEmpty()

    val enderChest = readEnderChest(input)

    return OfflinePlayerInfo(
        uuid = id.toString(),
        name = lastKnownName(id),
        lastPlayed = lastPlayed(id),
        online = isOnline(id),
        dimension = input.getStringOr("Dimension", "minecraft:overworld"),
        position = (input.read("Pos", Vec3.CODEC).getOrNull() ?: Vec3.ZERO).toVector(),
        rotation = Rotation(facing.x, facing.y),
        gameMode = gameType.serializedName,
        health = input.getFloatOr("Health", Player.MAX_HEALTH.toFloat()),
        foodLevel = food.foodLevel,
        saturation = food.saturationLevel,
        experienceLevel = input.getIntOr("XpLevel", 0),
        experienceProgress = input.getFloatOr("XpP", 0f),
        totalExperience = input.getIntOr("XpTotal", 0),
        inventory = contents(readInventory(input), input.getIntOr("SelectedItemSlot", 0)),
        enderChest = contents(enderChest, 0),
        effects = effects.map { it.toInfo() },
        spawnPoint =
            input
                .read("respawn", ServerPlayer.RespawnConfig.CODEC)
                .getOrNull()
                ?.respawnData()
                ?.toSpawnPoint(),
        lastDeath = input.read("LastDeathLocation", GlobalPos.CODEC).getOrNull()?.toDimensionPos(),
    )
}

/** Returns the problems the game would have loading the data, or null if there are none. */
internal fun MinecraftServer.problemsIn(id: UUID, data: CompoundTag): String? {
    val problems = ProblemReporter.Collector()
    describePlayerData(id, data, problems)

    return if (problems.isEmpty) null else problems.report
}
