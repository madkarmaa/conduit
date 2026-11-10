package top.madkarma.conduit.service.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.SummonCommand
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import top.madkarma.conduit.game.*
import top.madkarma.conduit.mcp.ToolInput

private const val UNLOADED_CHUNK_MESSAGE =
    "Chunk is not loaded; force-load it with the force_chunk action of edit_world or move " +
        "a player nearby"

data class SpawnRequest(
    val type: String,
    val dimension: String?,
    /** The spawn position, or the offset from the feet of the entity that `at` names. */
    val position: Vector3,
    /** An entity UUID or player name, whose position is read at spawn time. */
    val at: String? = null,
    val rotation: Rotation? = null,
    /** Applied on spawn, like the /summon nbt argument. */
    val snbt: String? = null,
    /** Runs the natural spawn setup, such as equipment and variants, like /summon without nbt. */
    val initialize: Boolean = true,
)

/** The spawn action's input. Without at, it needs a position. */
internal fun spawnRequest(input: ToolInput): SpawnRequest {
    val at = input.stringOrNull("at")
    if (at == null) input.require("position")
    return SpawnRequest(
        type = input.string("type"),
        dimension = input.stringOrNull("dimension"),
        position = input.decodeOrNull("position") ?: Vector3(0.0, 0.0, 0.0),
        at = at,
        rotation = input.decodeOrNull<Rotation>("rotation"),
        snbt = input.stringOrNull("snbt"),
        initialize = input.boolean("initialize"),
    )
}

internal fun MinecraftServer.describeEntity(uuid: String): EntityInfo = entity(uuid).toInfo()

internal fun MinecraftServer.spawnEntity(request: SpawnRequest): EntityInfo {
    val key = ResourceKey.create(Registries.ENTITY_TYPE, parseId(request.type))

    val type =
        registryAccess().lookupOrThrow(Registries.ENTITY_TYPE).get(key).orElseGet {
            invalidInput("Unknown entity type: ${request.type}")
        }

    // Reading where the entity is now, instead of where an earlier call saw it, keeps up with it.
    val anchor = request.at?.let(::entityOrPlayer)
    val level = anchor?.level() as ServerLevel? ?: level(request.dimension)
    val position = anchor?.position()?.add(request.position.toMc()) ?: request.position.toMc()
    requireEntityTicking(level, position)

    val snbt = request.snbt.orEmpty()
    val tag = if (snbt.isBlank()) CompoundTag() else TagParser.parseCompoundFully(snbt)
    val source = createCommandSourceStack().withLevel(level).withSuppressedOutput()
    val entity = SummonCommand.createEntity(source, type, position, tag, request.initialize)

    request.rotation?.let { rotate(entity, it.yaw, it.pitch) }

    // Spawn setup can lower the max health without lowering the health, since horses get a random
    // max health. The entity's first tick would lower the health to match, so this does it now.
    if (entity is LivingEntity && entity.health > entity.maxHealth) entity.health = entity.maxHealth

    entity.selfAndPassengers.forEach(EditJournal::spawned)

    return entity.toInfo()
}

// The game saves entities added to unloaded chunks to disk and unloads them right away.
internal fun requireEntityTicking(level: ServerLevel, position: Vec3) {
    if (level.isPositionEntityTicking(BlockPos.containing(position))) return
    failedPrecondition(UNLOADED_CHUNK_MESSAGE)
}
