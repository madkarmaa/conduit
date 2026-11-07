package top.madkarma.conduit.service.server

import net.minecraft.server.MinecraftServer

/** Writes worlds and players to disk. With flush, it waits until everything is written. */
internal fun MinecraftServer.saveAll(flush: Boolean) {
    saveEverything(false, flush, true)
}

/** Schedules the shutdown instead of running it inline, so the current call can still complete. */
internal fun MinecraftServer.scheduleStop() {
    execute { halt(false) }
}
