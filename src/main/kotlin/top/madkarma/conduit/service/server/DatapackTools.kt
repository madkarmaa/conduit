package top.madkarma.conduit.service.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.GameServer
import top.madkarma.conduit.mcp.*

private const val MAX_CONTENT_LENGTH = 200_000

val DATAPACKS =
    ToolDefinition(
        name = "datapacks",
        title = "Datapacks",
        description =
            "list shows every datapack and whether it is enabled. enable and disable turn one " +
                "on or off and reload the data, like /datapack. write adds a recipe, loot table, " +
                "function, advancement, predicate, item modifier or tag to the world's Conduit " +
                "pack, after checking it the way the game reads it. files lists the pack's " +
                "files, read shows one and delete removes one. reload applies the pack's files " +
                "and enables the pack, so write several files and then reload once. Functions " +
                "run with run_command \"function <id>\".",
        input =
            input {
                optional(
                    "pack",
                    stringSchema(
                        "Datapack id, e.g. \"file/my_pack\". Required for enable and disable.",
                    ),
                )
                optional(
                    "type",
                    enumSchema(
                        PackFileType.entries.map { it.name.lowercase() },
                        "Required for write, read and delete.",
                    ),
                )
                optional("id", stringSchema("File id, e.g. \"conduit:diamond_from_dirt\"."))
                optional(
                    "registry",
                    stringSchema("For tags, e.g. \"block\", \"item\" or \"function\"."),
                )
                optional(
                    "content",
                    stringSchema(
                        "For write: the JSON like in a datapack, or a function's commands, one per line.",
                        maxLength = MAX_CONTENT_LENGTH,
                    ),
                )
            },
        defaultAction = "list",
        actions =
            mapOf(
                "list" to readAction { server, _ -> json(server.listDatapacks()) },
                "enable" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        json(changeDatapackEnabled(input.string("pack"), enabled = true))
                    },
                "disable" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        json(changeDatapackEnabled(input.string("pack"), enabled = false))
                    },
                "files" to
                    AsyncAction(Access.READ) { _, _ ->
                        val files = onDisk { listPackFiles() }
                        lines(
                            "${files.size} files in the Conduit pack",
                            files,
                            dataOf("files" to encoded(files)),
                        )
                    },
                "read" to
                    AsyncAction(Access.READ) { input, _ ->
                        done(onDisk { readPackFile(packFile(input)) })
                    },
                "write" to
                    AsyncAction(Access.WRITE) { input, _ ->
                        val file = packFile(input)
                        val content = input.string("content")
                        GameServer.run { it.validatePackFile(file, content) }
                        onDisk { writePackFile(file, content) }
                        done("Wrote ${file.id}. Reload to apply it.")
                    },
                "delete" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        val file = packFile(input)
                        onDisk { deletePackFile(file) }
                        done("Deleted ${file.id}. Reload to apply it.")
                    },
                "reload" to
                    AsyncAction(Access.WRITE) { _, _ ->
                        reloadWithDatapack(CONDUIT_PACK_ID)
                        done("Data reloaded with the Conduit pack")
                    },
            ),
    )

private fun packFile(input: ToolInput) =
    PackFile(
        type = PackFileType.valueOf(input.string("type").uppercase()),
        id = input.string("id"),
        registry = input.stringOrNull("registry"),
    )

private suspend fun <T> onDisk(work: MinecraftServer.() -> T): T {
    val server = GameServer.require()
    return withContext(Dispatchers.IO) { server.work() }
}
