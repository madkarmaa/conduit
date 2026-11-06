package top.madkarma.conduit.service.batch

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.proto.Operation
import top.madkarma.conduit.proto.Operation.OperationCase.BROADCAST
import top.madkarma.conduit.proto.Operation.OperationCase.EXECUTE_COMMAND
import top.madkarma.conduit.proto.OperationResult
import top.madkarma.conduit.service.server.broadcast
import top.madkarma.conduit.service.server.executeCommand

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
