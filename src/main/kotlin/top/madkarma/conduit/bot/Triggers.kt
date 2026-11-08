package top.madkarma.conduit.bot

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import top.madkarma.conduit.config.ConduitConfig
import top.madkarma.conduit.config.TriggerConfig
import top.madkarma.conduit.events.EventBus
import top.madkarma.conduit.events.GameEvent

private val LOGGER = LoggerFactory.getLogger("conduit")

private const val TICKS_PER_SECOND = 20

/** One start of a trigger, with the event that started it, or none for a timed trigger. */
class TriggerRun(val config: TriggerConfig, val event: GameEvent?) {
    /** Runs show up like a request of a player with this name, who is never online. */
    val asker =
        Asker(
            "trigger ${config.name}",
            UUID.nameUUIDFromBytes("conduit-trigger:${config.name}".toByteArray()),
        )
}

/**
 * Starts full mode runs on game events and on timers. A trigger waits for its cooldown, and never
 * has more than one run in line, so events that its own run causes cannot pile up.
 */
object Triggers {
    @Volatile private var triggers: List<TriggerConfig> = emptyList()
    private val lastStarts = ConcurrentHashMap<String, Long>()
    private val listener: (GameEvent) -> Unit = ::onEvent

    /** Applies the triggers of a new config. Events are only built while a trigger needs them. */
    fun apply(config: ConduitConfig) {
        triggers = config.triggers.filter { it.enabled }

        EventBus.stopListening(listener)
        if (triggers.any { it.events.isNotEmpty() }) EventBus.listen(listener)
    }

    /** Starts the timed triggers that are due. Called on the server thread every tick. */
    fun onTick(server: MinecraftServer) {
        if (server.tickCount % TICKS_PER_SECOND != 0) return

        val now = System.currentTimeMillis()
        for (trigger in triggers.filter { it.everyMinutes > 0 }) {
            // A timer starts counting when the server starts or the trigger appears.
            val last = lastStarts.getOrPut(trigger.name) { now }
            if (now - last >= trigger.everyMinutes * 60_000L) start(TriggerRun(trigger, null))
        }
    }

    /** When each trigger last started a run, by name. */
    fun lastStarts(): Map<String, Long> = lastStarts.toMap()

    private fun onEvent(event: GameEvent) {
        for (trigger in triggers) {
            if (event.type.id !in trigger.events) continue
            if (trigger.players.isNotEmpty() && !event.involves(trigger.players)) continue
            start(TriggerRun(trigger, event))
        }
    }

    private fun start(run: TriggerRun) {
        val name = run.config.name
        val now = System.currentTimeMillis()
        val last = lastStarts[name]
        if (last != null && run.event != null && now - last < run.config.cooldownSeconds * 1000L)
            return
        if (Bot.line(Mode.FULL).has { it.trigger?.config?.name == name }) return

        lastStarts[name] = now
        LOGGER.info("Trigger {} starts a run", name)
        Bot.line(Mode.FULL).add(BotRequest(run.asker, run.config.prompt, Mode.FULL, run))
    }
}
