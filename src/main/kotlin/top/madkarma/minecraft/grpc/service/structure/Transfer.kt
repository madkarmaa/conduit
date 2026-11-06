package top.madkarma.minecraft.grpc.service.structure

import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import top.madkarma.minecraft.grpc.MinecraftGRPC
import top.madkarma.minecraft.grpc.game.GameServer
import top.madkarma.minecraft.grpc.proto.ExportStructureRequest
import top.madkarma.minecraft.grpc.proto.ImportStructureRequest
import top.madkarma.minecraft.grpc.proto.ImportStructureResponse
import top.madkarma.minecraft.grpc.proto.StructureData
import top.madkarma.minecraft.grpc.proto.StructurePlacement
import top.madkarma.minecraft.grpc.proto.structureData
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import java.io.ByteArrayOutputStream
import kotlin.math.min

// Well below gRPC's default 4 MiB message limit, so large files stream in several pieces.
private const val PIECE_BYTES = 1024 * 1024

private class Upload(val placement: StructurePlacement, val data: ByteArray)

internal fun exportStructure(request: ExportStructureRequest): Flow<StructureData> = flow {
    val captured = GameServer.run {
        it.captureStructure(
            request.dimension,
            request.from,
            request.to,
            request.includeEntities,
            request.ignoreBlock,
        )
    }
    val file = withContext(Dispatchers.Default) { encodeStructure(captured.nbt) }

    for (offset in file.indices step PIECE_BYTES) {
        val piece = structureData {
            data = ByteString.copyFrom(file, offset, min(PIECE_BYTES, file.size - offset))
            if (offset == 0) size = captured.size.toProto()
        }

        emit(piece)
    }
}

internal suspend fun importStructure(
    requests: Flow<ImportStructureRequest>,
): ImportStructureResponse {
    val upload = receiveUpload(requests)
    val server = GameServer.require()
    val template = withContext(Dispatchers.Default) { server.decodeStructure(upload.data) }

    return GameServer.run { it.placeStructure(template, upload.placement) }
}

/** Fails as soon as the data exceeds the upload limit, without reading the rest of the stream. */
private suspend fun receiveUpload(requests: Flow<ImportStructureRequest>): Upload {
    val limit = MinecraftGRPC.config.maxStructureUploadBytes
    val data = ByteArrayOutputStream()
    var placement: StructurePlacement? = null

    requests.collect { request ->
        if (placement == null) {
            if (!request.hasHeader()) invalidArgument("The first message must be the header")

            checkPlacement(request.header)
            placement = request.header
            return@collect
        }

        if (!request.hasData()) invalidArgument("Only the first message may be a header")

        if (data.size().toLong() + request.data.size() > limit) {
            invalidArgument("Structure data exceeds the limit of $limit bytes")
        }

        request.data.writeTo(data)
    }

    val header = placement ?: invalidArgument("The stream ended before the header")
    if (data.size() == 0) invalidArgument("The stream contains no structure data")

    return Upload(header, data.toByteArray())
}
