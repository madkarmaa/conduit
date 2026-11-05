package top.madkarma.minecraft.grpc.service.registry

import com.mojang.serialization.JsonOps
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.LootTable
import top.madkarma.minecraft.grpc.proto.lootTable
import top.madkarma.minecraft.grpc.rpc.notFound
import kotlin.jvm.optionals.getOrNull
import top.madkarma.minecraft.grpc.proto.LootTable as ProtoLootTable

// ReloadableServerRegistries.Holder.getLootTable returns an empty table for unknown ids, so the
// lookup goes through the registry to tell missing tables apart.
internal fun MinecraftServer.readLootTable(id: String): ProtoLootTable {
    val key = ResourceKey.create(Registries.LOOT_TABLE, requireId(id, "loot table"))
    val registries = reloadableRegistries().lookup()
    val holder = registries.lookupOrThrow(Registries.LOOT_TABLE).get(key).getOrNull()
        ?: notFound("Unknown loot table: $id")

    val context = registries.createSerializationContext(JsonOps.INSTANCE)
    val encoded = LootTable.DIRECT_CODEC.encodeStart(context, holder.value()).getOrThrow()

    return lootTable {
        this.id = key.identifier().toString()
        json = encoded.toString()
    }
}
