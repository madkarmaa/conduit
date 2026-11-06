package top.madkarma.conduit.rpc

import io.grpc.BindableService
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import top.madkarma.conduit.config.GrpcConfig

object GrpcHost {
    private val LOGGER = LoggerFactory.getLogger("conduit")
    private val services = mutableListOf<BindableService>()
    private var server: Server? = null

    fun register(vararg service: BindableService) {
        check(server == null) { "Services must be registered before the gRPC server starts" }
        services += service
    }

    @Synchronized
    fun start(config: GrpcConfig) {
        if (server != null) return

        if (!config.enabled) {
            LOGGER.info("gRPC server disabled in config")
            return
        }

        if (!config.requireAuth) {
            LOGGER.warn(
                "gRPC authentication is disabled: anyone who can reach {}:{} controls the game",
                config.host,
                config.port,
            )
        }

        val health = HealthStatusManager()

        val builder =
            NettyServerBuilder.forAddress(InetSocketAddress(config.host, config.port))
                .intercept(AuthInterceptor(config))
                .addService(health.healthService)
                .addService(ProtoReflectionServiceV1.newInstance())

        // Interceptors run in reverse order, so the audit log also sees calls rejected by auth.
        if (config.auditLog) builder.intercept(AuditInterceptor())

        services.forEach { builder.addService(it) }
        server = builder.build().start()

        LOGGER.info(
            "gRPC server listening on {}:{} ({} services)",
            config.host,
            config.port,
            services.size,
        )
    }

    @Synchronized
    fun stop() {
        val current = server ?: return

        server = null
        current.shutdown()

        if (!current.awaitTermination(3, TimeUnit.SECONDS)) {
            current.shutdownNow()
        }

        LOGGER.info("gRPC server stopped")
    }
}
