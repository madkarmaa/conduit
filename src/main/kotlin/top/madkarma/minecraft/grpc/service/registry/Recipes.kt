package top.madkarma.minecraft.grpc.service.registry

import com.mojang.serialization.JsonOps
import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.BrewingRecipe
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.display.SlotDisplay
import top.madkarma.minecraft.grpc.game.toProto
import top.madkarma.minecraft.grpc.proto.ListRecipesRequest
import top.madkarma.minecraft.grpc.proto.Recipe as ProtoRecipe
import top.madkarma.minecraft.grpc.proto.RecipeList
import top.madkarma.minecraft.grpc.proto.recipe
import top.madkarma.minecraft.grpc.proto.recipeList
import top.madkarma.minecraft.grpc.rpc.notFound

internal fun MinecraftServer.readRecipes(request: ListRecipesRequest): RecipeList {
    val serializer =
        request.type
            .ifBlank { null }
            ?.let {
                BuiltInRegistries.RECIPE_SERIALIZER.requireValue(it, "recipe type")
            }

    val item =
        request.resultItem
            .ifBlank { null }
            ?.let {
                BuiltInRegistries.ITEM.requireValue(it, "item")
            }

    val matching =
        recipeManager.recipes
            .filter { serializer == null || it.value().serializer == serializer }
            .filter { item == null || it.value().fixedResult()?.item == item }
            .sortedBy { it.id().identifier().toString() }

    val registries = registryAccess()
    return recipeList {
        total = matching.size
        recipes += matching.page(request.offset, request.limit).map { it.toProto(registries) }
    }
}

internal fun MinecraftServer.readRecipe(id: String): ProtoRecipe {
    val key = ResourceKey.create(Registries.RECIPE, requireId(id, "recipe"))
    val holder = recipeManager.byKey(key).getOrNull() ?: notFound("Unknown recipe: $id")

    return holder.toProto(registryAccess())
}

private fun <T : Any> Registry<T>.requireValue(id: String, kind: String): T {
    val holder = get(requireId(id, kind)).getOrNull() ?: notFound("Unknown $kind: $id")
    return holder.value()
}

private fun RecipeHolder<*>.toProto(registries: HolderLookup.Provider): ProtoRecipe {
    val recipeId = id().identifier().toString()
    val definition = value()
    val resultStack = definition.fixedResult()
    val context = registries.createSerializationContext(JsonOps.INSTANCE)

    return recipe {
        id = recipeId
        type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(definition.serializer).toString()
        group = definition.group()

        if (resultStack != null) result = resultStack.toProto(registries)

        json = Recipe.DIRECT_CODEC.encodeStart(context, definition).getOrThrow().toString()
    }
}

// Special recipes, such as firework rockets, compute their result from the crafting input.
private fun Recipe<*>.fixedResult(): ItemStack? {
    // Brewing recipes have no recipe book display to read the result from.
    if (this is BrewingRecipe) return output.create()

    return when (val result = display().firstOrNull()?.result()) {
        is SlotDisplay.ItemStackSlotDisplay -> result.stack().create()
        is SlotDisplay.ItemSlotDisplay -> ItemStack(result.item())
        else -> null
    }
}
