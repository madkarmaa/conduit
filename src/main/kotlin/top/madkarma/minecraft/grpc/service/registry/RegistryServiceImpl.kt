package top.madkarma.minecraft.grpc.service.registry

import com.google.protobuf.Empty
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.*

class RegistryServiceImpl : RegistryServiceGrpcKt.RegistryServiceCoroutineImplBase() {
    override suspend fun listRegistries(request: Empty): RegistryList = GameServer.run {
        it.describeRegistries()
    }

    override suspend fun listRegistryEntries(
        request: ListRegistryEntriesRequest,
    ): RegistryEntryList = GameServer.run { it.readRegistryEntries(request) }

    override suspend fun listTags(request: ListTagsRequest): TagList = GameServer.run {
        it.readTags(request)
    }

    override suspend fun listRecipes(request: ListRecipesRequest): RecipeList = GameServer.run {
        it.readRecipes(request)
    }

    override suspend fun getRecipe(request: GetRecipeRequest): Recipe = GameServer.run {
        it.readRecipe(request.id)
    }

    override suspend fun getLootTable(request: GetLootTableRequest): LootTable = GameServer.run {
        it.readLootTable(request.id)
    }
}
