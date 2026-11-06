package top.madkarma.minecraft.grpc.service.structure

import com.google.protobuf.Empty
import kotlinx.coroutines.flow.Flow
import top.madkarma.minecraft.grpc.proto.*

private val EMPTY: Empty = Empty.getDefaultInstance()

class StructureServiceImpl : StructureServiceGrpcKt.StructureServiceCoroutineImplBase() {
    override fun export(request: ExportStructureRequest): Flow<StructureData> =
        exportStructure(request)

    override suspend fun `import`(requests: Flow<ImportStructureRequest>): ImportStructureResponse =
        importStructure(requests)

    override suspend fun save(request: SaveStructureRequest): Empty {
        saveStructure(request)
        return EMPTY
    }

    override suspend fun place(request: PlaceStructureRequest): ImportStructureResponse =
        placeStoredStructure(request)

    override suspend fun list(request: Empty): StructureList = listStructures()
}
