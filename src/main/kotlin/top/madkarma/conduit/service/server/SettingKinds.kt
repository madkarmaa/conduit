package top.madkarma.conduit.service.server

import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import top.madkarma.conduit.game.failedPrecondition
import top.madkarma.conduit.game.invalidInput

/**
 * One server.properties setting that a running dedicated server applies at once and saves, the way
 * the game's management protocol changes it.
 */
internal class ServerSetting(
    val name: String,
    /** Decides who may join or act, so set_access changes it instead of set. */
    val access: Boolean,
    /** The values it takes, for errors. */
    val accepts: String,
    val read: (DedicatedServer) -> String,
    val write: (DedicatedServer, String) -> Unit,
)

internal fun flag(
    name: String,
    read: (DedicatedServer) -> Boolean,
    write: (DedicatedServer, Boolean) -> Unit,
    access: Boolean = false,
) =
    ServerSetting(name, access, "true or false", { read(it).toString() }) { server, value ->
        write(server, value.toBooleanStrictOrNull() ?: invalidInput("$name takes true or false"))
    }

internal fun number(
    name: String,
    range: IntRange,
    read: (DedicatedServer) -> Int,
    write: (DedicatedServer, Int) -> Unit,
    access: Boolean = false,
): ServerSetting {
    val accepts =
        if (range.last == Int.MAX_VALUE) "a whole number of at least ${range.first}"
        else "a whole number from ${range.first} to ${range.last}"

    return ServerSetting(name, access, accepts, { read(it).toString() }) { server, value ->
        val number = value.toIntOrNull()?.takeIf { it in range }
        write(server, number ?: invalidInput("$name takes $accepts"))
    }
}

internal fun <T> choice(
    name: String,
    options: Map<String, T>,
    read: (DedicatedServer) -> T,
    write: (DedicatedServer, T) -> Unit,
): ServerSetting {
    val accepts = options.keys.joinToString(", ")
    val names = options.entries.associate { (key, option) -> option to key }

    return ServerSetting(name, false, accepts, { names.getValue(read(it)) }) { server, value ->
        write(server, options[value.lowercase()] ?: invalidInput("$name takes $accepts"))
    }
}

internal fun setting(name: String): ServerSetting =
    LIVE_SETTINGS.firstOrNull { it.name == name } ?: invalidInput("Unknown setting: $name")

internal fun MinecraftServer.dedicated(): DedicatedServer =
    this as? DedicatedServer
        ?: failedPrecondition(
            "Server settings only exist on a dedicated server, not in single player",
        )
