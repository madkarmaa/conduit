package top.madkarma.minecraft.grpc.rpc

import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import net.minecraft.util.thread.BlockableEventLoop
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.time.Duration.Companion.milliseconds

private val LOGGER = LoggerFactory.getLogger("minecraft_grpc")

private const val GAME_THREAD_TIMEOUT_MS = 30_000L

fun invalidArgument(message: String): Nothing =
    throw Status.INVALID_ARGUMENT.withDescription(message).asException()

fun notFound(message: String): Nothing =
    throw Status.NOT_FOUND.withDescription(message).asException()

fun failedPrecondition(message: String): Nothing =
    throw Status.FAILED_PRECONDITION.withDescription(message).asException()

fun aborted(message: String): Nothing = throw Status.ABORTED.withDescription(message).asException()

fun unavailable(message: String): Nothing =
    throw Status.UNAVAILABLE.withDescription(message).asException()

fun Throwable.toStatusException(): StatusException {
    val cause = (this as? CompletionException)?.cause ?: this
    if (cause is StatusException) return cause

    // Checked before IllegalStateException, which it extends.
    if (cause is TimeoutCancellationException) {
        return Status.DEADLINE_EXCEEDED.withDescription("Game thread did not respond in time")
            .asException()
    }

    val status = when (cause) {
        is CommandSyntaxException, is IllegalArgumentException -> Status.INVALID_ARGUMENT
        is IllegalStateException -> Status.FAILED_PRECONDITION
        else -> null
    }

    if (status != null) return status.withDescription(cause.message).withCause(cause).asException()

    LOGGER.error("Unexpected error while handling gRPC call", cause)
    return Status.INTERNAL.withDescription(cause.toString()).withCause(cause).asException()
}

suspend fun <T> BlockableEventLoop<*>.call(block: () -> T): T {
    try {
        if (isSameThread) return block()

        return withTimeout(GAME_THREAD_TIMEOUT_MS.milliseconds) {
            CompletableFuture.supplyAsync({ block() }, this@call).await()
        }
    } catch (exception: Throwable) {
        if (exception is CancellationException && exception !is TimeoutCancellationException) {
            throw exception
        }

        throw exception.toStatusException()
    }
}
