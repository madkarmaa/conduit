package top.madkarma.minecraft.grpc.rpc

import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.TextFormat
import io.grpc.*
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.health.v1.HealthGrpc
import io.grpc.reflection.v1.ServerReflectionGrpc
import org.slf4j.LoggerFactory
import top.madkarma.minecraft.grpc.proto.EventServiceGrpc
import java.util.concurrent.TimeUnit

private val AUDIT_LOGGER = LoggerFactory.getLogger("minecraft_grpc/audit")

private const val MAX_REQUEST_LENGTH = 500

private val READ_ONLY_SERVICES = setOf(HealthGrpc.SERVICE_NAME, ServerReflectionGrpc.SERVICE_NAME)

private val READ_ONLY_PREFIXES = listOf("Get", "List")

private val REQUEST_PRINTER = TextFormat.printer().emittingSingleLine(true)

private fun isReadOnly(method: MethodDescriptor<*, *>): Boolean {
    if (method.serviceName in READ_ONLY_SERVICES) return true
    if (method.fullMethodName == EventServiceGrpc.getSubscribeMethod().fullMethodName) return true

    val name = method.bareMethodName ?: return false
    return READ_ONLY_PREFIXES.any { name.startsWith(it) }
}

class AuditInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val auditedCall = AuditedCall(call)
        val listener = next.startCall(auditedCall, headers)

        return object : SimpleForwardingServerCallListener<ReqT>(listener) {
            override fun onMessage(message: ReqT) {
                auditedCall.request = message
                super.onMessage(message)
            }
        }
    }
}

private class AuditedCall<ReqT, RespT>(call: ServerCall<ReqT, RespT>) :
    SimpleForwardingServerCall<ReqT, RespT>(call) {
    private val startNanos = System.nanoTime()

    @Volatile
    var request: Any? = null

    override fun close(status: Status, trailers: Metadata) {
        super.close(status, trailers)
        log(status)
    }

    private fun log(status: Status) {
        val method = methodDescriptor.fullMethodName
        val address = attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR) ?: "unknown"

        if (status.code == Status.Code.UNAUTHENTICATED) {
            AUDIT_LOGGER.warn("unauthenticated call to {} from {}", method, address)
            return
        }

        if (isReadOnly(methodDescriptor)) return

        val durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        AUDIT_LOGGER.info(
            "{} from {} -> {} in {} ms: {}",
            method,
            address,
            status.code,
            durationMillis,
            describe(request),
        )
    }
}

private fun describe(request: Any?): String {
    val message = request as? MessageOrBuilder ?: return ""

    val text = REQUEST_PRINTER.printToString(message)
    if (text.length <= MAX_REQUEST_LENGTH) return text

    return text.take(MAX_REQUEST_LENGTH) + "…"
}
