package top.madkarma.conduit.service.registry

import com.mojang.serialization.JsonOps
import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
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
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.notFound
import top.madkarma.conduit.game.toInfo

@Serializable
data class RecipeInfo(
    val id: String,
    val type: String,
    val group: String,
    /** Set when the result does not depend on the crafting input. */
    val result: ItemInfo?,
    /** The full recipe in the datapack JSON format. */
    val json: String,
)

@Serializable data class RecipePage(val total: Int, val recipes: List<RecipeInfo>)

/**
 * Recipes in id order, optionally only of one type as in the recipe JSON, such as
 * "minecraft:smelting", or only those producing one item.
 */
internal fun MinecraftServer.readRecipes(
    type: String?,
    resultItem: String?,
    offset: Int,
    limit: Int,
): RecipePage {
    val serializer =
        type
            ?.ifBlank { null }
            ?.let { BuiltInRegistries.RECIPE_SERIALIZER.requireValue(it, "recipe type") }

    val item = resultItem?.ifBlank { null }?.let { BuiltInRegistries.ITEM.requireValue(it, "item") }

    val matching =
        recipeManager.recipes
            .filter { serializer == null || it.value().serializer == serializer }
            .filter { item == null || it.value().fixedResult()?.item == item }
            .sortedBy { it.id().identifier().toString() }

    val registries = registryAccess()
    return RecipePage(matching.size, matching.page(offset, limit).map { it.toInfo(registries) })
}

internal fun MinecraftServer.readRecipe(id: String): RecipeInfo {
    val key = ResourceKey.create(Registries.RECIPE, requireId(id, "recipe"))
    val holder = recipeManager.byKey(key).getOrNull() ?: notFound("Unknown recipe: $id")

    return holder.toInfo(registryAccess())
}

private fun <T : Any> Registry<T>.requireValue(id: String, kind: String): T {
    val holder = get(requireId(id, kind)).getOrNull() ?: notFound("Unknown $kind: $id")
    return holder.value()
}

private fun RecipeHolder<*>.toInfo(registries: HolderLookup.Provider): RecipeInfo {
    val recipeId = id().identifier().toString()
    val definition = value()
    val resultStack = definition.fixedResult()
    val context = registries.createSerializationContext(JsonOps.INSTANCE)

    return RecipeInfo(
        id = recipeId,
        type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(definition.serializer).toString(),
        group = definition.group(),
        result = resultStack?.toInfo(registries),
        json = Recipe.DIRECT_CODEC.encodeStart(context, definition).getOrThrow().toString(),
    )
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
