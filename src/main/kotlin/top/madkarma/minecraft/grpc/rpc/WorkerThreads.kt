package top.madkarma.minecraft.grpc.rpc

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// A cached pool gives every job a thread of its own, so a long job never delays another one, and
// reuses idle threads. It is never shut down: the threads are daemons and end after a minute
// without work, so the same pool also serves the next world opened in singleplayer.
private val WORKER_EXECUTOR: ExecutorService = Executors.newCachedThreadPool(
    Thread.ofPlatform().name("minecraft-grpc-worker-", 1).daemon().factory(),
)

private val WORKER_DISPATCHER = WORKER_EXECUTOR.asCoroutineDispatcher()

/**
 * Runs intensive work that needs no game state on a worker thread, so it blocks neither the gRPC
 * threads nor the server thread. A started block always runs to the end, even when the call is
 * cancelled, so it never leaves a half-written file behind.
 */
suspend fun <T> runInWorkerThread(block: () -> T): T = withContext(WORKER_DISPATCHER) { block() }

/** Starts work on a worker thread without waiting for it to finish. */
fun startInWorkerThread(block: () -> Unit) = WORKER_EXECUTOR.execute(block)
