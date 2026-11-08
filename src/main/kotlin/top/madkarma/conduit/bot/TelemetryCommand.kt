package top.madkarma.conduit.bot

import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import top.madkarma.conduit.Conduit
import top.madkarma.conduit.LOGGER

/** /conduit telemetry, which turns telemetry on or off in the config file, or empties its file. */
object TelemetryCommand {
    fun show(source: CommandSourceStack) {
        val state = if (Conduit.config?.telemetry == true) "on" else "off"
        source.sendSystemMessage(botMessage("Telemetry is $state. Its file is ${Telemetry.FILE}."))
    }

    fun change(source: CommandSourceStack, enabled: Boolean) {
        val problems = Conduit.updateConfig { written ->
            JsonObject(written + ("telemetry" to JsonPrimitive(enabled)))
        }

        if (problems.isNotEmpty()) {
            source.sendFailure(Component.literal("The config is invalid, so nothing changed:"))
            return problems.forEach { source.sendFailure(Component.literal("- $it")) }
        }

        val state = if (enabled) "on. Requests go to ${Telemetry.FILE}" else "off"
        source.sendSystemMessage(botMessage("Telemetry is now $state."))
    }

    fun clear(source: CommandSourceStack) {
        try {
            Telemetry.clear()
        } catch (exception: IOException) {
            LOGGER.warn("Cannot clear {}: {}", Telemetry.FILE, exception.toString())
            return source.sendFailure(Component.literal("Cannot clear ${Telemetry.FILE}."))
        }

        source.sendSystemMessage(botMessage("Cleared ${Telemetry.FILE}."))
    }
}
