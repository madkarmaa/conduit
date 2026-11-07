package top.madkarma.conduit.rpc

import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.grpc.Status
import io.grpc.StatusException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import net.minecraft.util.thread.BlockableEventLoop
import org.slf4j.LoggerFactory
import top.madkarma.conduit.game.ConduitError

private val LOGGER = LoggerFactory.getLogger("conduit")

private const val GAME_THREAD_TIMEOUT_MS = 30_000L

fun Throwable.toStatusException(): StatusException {
    val cause = (this as? CompletionException)?.cause ?: this
    if (cause is StatusException) return cause
    if (cause is ConduitError) {
        return Status.fromCode(Status.Code.valueOf(cause.code))
            .withDescription(cause.message)
            .asException()
    }

    // Checked before IllegalStateException, which it extends.
    if (cause is TimeoutCancellationException) {
        return Status.DEADLINE_EXCEEDED.withDescription("Game thread did not respond in time")
            .asException()
    }

    val status =
        when (cause) {
            is CommandSyntaxException,
            is IllegalArgumentException -> Status.INVALID_ARGUMENT
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
