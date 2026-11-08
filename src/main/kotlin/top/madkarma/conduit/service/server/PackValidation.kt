package top.madkarma.conduit.service.server

import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.advancements.Advancement
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagFile
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.parseId

/**
 * Checks a file the way the game reads it, so a broken file fails here instead of being skipped
 * with only a log line when the data reloads.
 */
internal fun MinecraftServer.validatePackFile(file: PackFile, content: String) {
    if (file.type == PackFileType.FUNCTION) return validateFunction(file, content)

    val json =
        try {
            JsonParser.parseString(content)
        } catch (exception: RuntimeException) {
            invalidInput("Invalid JSON: ${exception.message}")
        }

    val ops = registryAccess().createSerializationContext(JsonOps.INSTANCE)
    codecOf(file.type).parse(ops, json).error().ifPresent {
        invalidInput("Invalid ${file.type.folder}: ${it.message()}")
    }
}

private fun codecOf(type: PackFileType): Codec<*> =
    when (type) {
        PackFileType.RECIPE -> Recipe.DIRECT_CODEC
        PackFileType.LOOT_TABLE -> LootTable.DIRECT_CODEC
        PackFileType.ADVANCEMENT -> Advancement.CODEC
        PackFileType.PREDICATE -> LootItemCondition.DIRECT_CODEC
        PackFileType.ITEM_MODIFIER -> LootItemFunctions.DIRECT_CODEC
        PackFileType.TAG -> TagFile.CODEC
        PackFileType.FUNCTION -> error("Functions are commands, not JSON")
    }

private fun MinecraftServer.validateFunction(file: PackFile, content: String) {
    try {
        CommandFunction.fromLines(
            parseId(file.id),
            commands.dispatcher,
            createCommandSourceStack(),
            content.lines(),
        )
    } catch (exception: IllegalArgumentException) {
        invalidInput("Invalid function: ${exception.message}")
    }
}
