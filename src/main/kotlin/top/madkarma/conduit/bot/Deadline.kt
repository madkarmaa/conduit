package top.madkarma.conduit.bot

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private const val CHECK_INTERVAL_MS = 250L

/**
 * The time a request may take. It stops counting while an approval waits for an answer, so the time
 * a player takes to decide does not count against the request.
 */
class Deadline(seconds: Int) {
    private var endsAt = System.currentTimeMillis() + seconds * 1000L
    private var pausedSince: Long? = null
    private var pauses = 0

    @Synchronized
    fun pause() {
        if (pauses++ == 0) pausedSince = System.currentTimeMillis()
    }

    @Synchronized
    fun resume() {
        if (--pauses > 0) return
        endsAt += System.currentTimeMillis() - (pausedSince ?: return)
        pausedSince = null
    }

    @Synchronized
    private fun expired(): Boolean = pausedSince == null && System.currentTimeMillis() >= endsAt

    suspend fun awaitExpiry() {
        while (!expired()) delay(CHECK_INTERVAL_MS.milliseconds)
    }
}
