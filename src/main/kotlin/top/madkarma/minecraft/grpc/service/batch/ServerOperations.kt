package top.madkarma.minecraft.grpc.service.batch

import net.minecraft.server.MinecraftServer
import top.madkarma.minecraft.grpc.proto.Operation
import top.madkarma.minecraft.grpc.proto.Operation.OperationCase.*
import top.madkarma.minecraft.grpc.proto.OperationResult
import top.madkarma.minecraft.grpc.service.server.broadcast
import top.madkarma.minecraft.grpc.service.server.executeCommand

/** Returns false when the operation is not a ServerService operation. */
internal fun MinecraftServer.applyServerOperation(
    operation: Operation,
    result: OperationResult.Builder,
): Boolean {
    when (operation.operationCase) {
        EXECUTE_COMMAND -> result.executeCommand = executeCommand(operation.executeCommand)
        BROADCAST -> broadcast(operation.broadcast)
        else -> return false
    }

    return true
}
