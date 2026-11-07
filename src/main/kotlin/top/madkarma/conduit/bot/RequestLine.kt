package top.madkarma.conduit.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One request, from the moment a player asks until the bot answers it. */
class BotRequest(val asker: Asker, val text: String, val mode: Mode) {
    var job: Job? = null
}

/**
 * The requests of one mode, which the bot answers one at a time, like one player in the chat.
 * Everyone can ask at once, and the requests wait in the order they arrived.
 */
class RequestLine(
    private val scope: CoroutineScope,
    private val answer: suspend (BotRequest) -> Unit,
) {
    private val waiting = ArrayDeque<BotRequest>()
    private var running: BotRequest? = null
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { for (signal in wakeUp) answerWaiting() }
    }

    /** Returns how many requests come before this one. */
    fun add(request: BotRequest): Int {
        val ahead =
            synchronized(this) {
                waiting.addLast(request)
                waiting.size - 1 + if (running != null) 1 else 0
            }
        wakeUp.trySend(Unit)
        return ahead
    }

    @Synchronized
    fun has(predicate: (BotRequest) -> Boolean): Boolean =
        running?.let(predicate) == true || waiting.any(predicate)

    @Synchronized fun running(): BotRequest? = running

    @Synchronized fun waiting(): List<BotRequest> = waiting.toList()

    /**
     * Takes waiting requests out of the line and cancels the running one, if they match. Returns
     * the requests it cancelled.
     */
    fun cancel(predicate: (BotRequest) -> Boolean): List<BotRequest> {
        val (removed, current) =
            synchronized(this) {
                val matching = waiting.filter(predicate)
                waiting.removeAll(matching)
                matching to running?.takeIf(predicate)
            }

        current?.job?.cancel()
        return removed + listOfNotNull(current)
    }

    private suspend fun answerWaiting() {
        while (true) {
            // The job exists before the request counts as running, so a cancel always reaches it.
            val job =
                synchronized(this) {
                    val next = waiting.removeFirstOrNull() ?: return
                    scope
                        .launch(start = CoroutineStart.LAZY) { answer(next) }
                        .also {
                            next.job = it
                            running = next
                        }
                }

            try {
                job.join()
            } finally {
                synchronized(this) { running = null }
            }
        }
    }
}
