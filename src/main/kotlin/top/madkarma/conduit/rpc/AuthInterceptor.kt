package top.madkarma.conduit.rpc

import io.grpc.*
import java.security.MessageDigest
import top.madkarma.conduit.config.GrpcConfig

class AuthInterceptor(private val config: GrpcConfig) : ServerInterceptor {
    private val header = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    private val expected = "Bearer ${config.token}".toByteArray()

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        if (!requiresToken(call) || isAuthorized(headers)) return next.startCall(call, headers)

        val status = Status.UNAUTHENTICATED.withDescription("Missing or invalid bearer token")
        call.close(status, Metadata())

        return object : ServerCall.Listener<ReqT>() {}
    }

    private fun requiresToken(call: ServerCall<*, *>): Boolean =
        config.requireAuth && call.methodDescriptor.serviceName != HEALTH_SERVICE

    private fun isAuthorized(headers: Metadata): Boolean {
        val provided = headers.get(header)?.toByteArray() ?: return false
        return MessageDigest.isEqual(provided, expected)
    }

    private companion object {
        const val HEALTH_SERVICE = "grpc.health.v1.Health"
    }
}
