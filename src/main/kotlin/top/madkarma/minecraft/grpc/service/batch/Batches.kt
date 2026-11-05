package top.madkarma.minecraft.grpc.service.batch

import io.grpc.Status
import net.minecraft.server.MinecraftServer
import top.madkarma.minecraft.grpc.proto.*
import top.madkarma.minecraft.grpc.rpc.invalidArgument
import top.madkarma.minecraft.grpc.rpc.toStatusException

private const val MAX_OPERATIONS = 10_000

private val SKIPPED: OperationResult = operationResult {
    code = Status.Code.CANCELLED.value()
    skipped = true
}

internal fun MinecraftServer.executeBatch(request: BatchRequest): BatchResponse {
    if (request.operationsCount > MAX_OPERATIONS) {
        invalidArgument(
            "A batch holds at most $MAX_OPERATIONS operations, got ${request.operationsCount}",
        )
    }

    var failed = false

    return batchResponse {
        for (operation in request.operationsList) {
            if (failed && request.stopOnError) {
                results += SKIPPED
                continue
            }

            val result = runOperation(operation)
            if (result.code != Status.Code.OK.value()) failed = true
            results += result
        }
    }
}

// An operation fails exactly like its RPC would, without aborting the rest of the batch.
private fun MinecraftServer.runOperation(operation: Operation): OperationResult {
    val result = OperationResult.newBuilder()

    try {
        applyOperation(operation, result)
    } catch (exception: Exception) {
        val status = exception.toStatusException().status
        result.code = status.code.value()
        result.message = status.description.orEmpty()
    }

    return result.build()
}

private fun MinecraftServer.applyOperation(operation: Operation, result: OperationResult.Builder) {
    val applied = applyWorldOperation(operation, result) ||
        applyEntityOperation(operation, result) ||
        applyPlayerOperation(operation, result) ||
        applyServerOperation(operation, result)

    if (!applied) invalidArgument("Operation is not set or not supported by this server")
}
