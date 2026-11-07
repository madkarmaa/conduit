package top.madkarma.conduit.service.structure

import com.google.protobuf.ByteString
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.proto.*
import top.madkarma.conduit.rpc.runInWorkerThread

// Well below gRPC's default 4 MiB message limit, so large files stream in several pieces.
private const val PIECE_BYTES = 1024 * 1024

private class Upload(val placement: StructurePlacement, val data: ByteArray)

internal fun exportStructure(request: ExportStructureRequest): Flow<StructureData> = flow {
    val template = GameServer.run {
        it.captureStructure(
            request.dimension,
            request.from,
            request.to,
            request.includeEntities,
            request.ignoreBlock,
        )
    }
    val file = runInWorkerThread { encodeStructure(template) }

    for (offset in file.indices step PIECE_BYTES) {
        val piece = structureData {
            data = ByteString.copyFrom(file, offset, min(PIECE_BYTES, file.size - offset))
            if (offset == 0) size = template.size.toProto()
        }

        emit(piece)
    }
}

internal suspend fun importStructure(
    requests: Flow<ImportStructureRequest>,
): ImportStructureResponse {
    val upload = receiveUpload(requests)
    val server = GameServer.require()
    val template = runInWorkerThread { server.decodeStructure(upload.data) }

    return GameServer.run { it.placeStructure(template, upload.placement) }
}

/** Fails as soon as the data exceeds the upload limit, without reading the rest of the stream. */
private suspend fun receiveUpload(requests: Flow<ImportStructureRequest>): Upload {
    val limit = Conduit.config.maxStructureUploadBytes
    val data = ByteArrayOutputStream()
    var placement: StructurePlacement? = null

    requests.collect { request ->
        if (placement == null) {
            if (!request.hasHeader()) invalidInput("The first message must be the header")

            checkPlacement(request.header)
            placement = request.header
            return@collect
        }

        if (!request.hasData()) invalidInput("Only the first message may be a header")

        if (data.size().toLong() + request.data.size() > limit) {
            invalidInput("Structure data exceeds the limit of $limit bytes")
        }

        request.data.writeTo(data)
    }

    val header = placement ?: invalidInput("The stream ended before the header")
    if (data.size() == 0) invalidInput("The stream contains no structure data")

    return Upload(header, data.toByteArray())
}
