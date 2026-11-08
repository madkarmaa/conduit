package top.madkarma.conduit.service.history

import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable
import top.madkarma.conduit.game.EditGroup
import top.madkarma.conduit.game.EditJournal
import top.madkarma.conduit.mcp.*

@Serializable
data class EditSummary(
    val id: Int,
    /** Who made the edit, such as "Steve in full mode" or "token desktop". */
    val caller: String,
    /** The tool and action, such as "edit_blocks.fill". */
    val action: String,
    val time: String,
    val blocks: Long,
    val entities: Int,
    val dimensions: List<String>,
)

val UNDO =
    ToolDefinition(
        name = "undo",
        title = "Undo edits",
        description =
            "The server records the block changes of edit_blocks, edit_world explode, " +
                "structures place and copy, and inventory on containers, and the entities that " +
                "edit_entity spawns and removes, one edit per call. list shows the recent " +
                "edits, newest first. undo restores the blocks and block entities of the given " +
                "edits, or of your newest one, removes the entities they spawned and brings " +
                "back the ones they removed. It does not bring back dropped items or other " +
                "changes to entities, and it cannot undo run_command, fire spread, falling " +
                "blocks or anything before a server restart.",
        seesOtherPlayers = true,
        input =
            input {
                optional(
                    "ids",
                    arraySchema(integerSchema(min = 1), "Edits to undo. Defaults to your newest."),
                )
                optional("limit", LIMIT)
            },
        actions =
            mapOf(
                "list" to
                    readAction { _, input ->
                        val edits = EditJournal.groups().take(input.int("limit")).map(::summarize)
                        lines(
                            "${edits.size} recent edits",
                            edits.map(::describe),
                            dataOf("edits" to encoded(edits)),
                        )
                    },
                "undo" to
                    destructiveAction { server, input ->
                        val ids = input.strings("ids").map { it.toInt() }
                        val undone = EditJournal.undo(server, ids, input.policy.name)
                        val edits = undone.map(::summarize)
                        lines(
                            "Undid ${edits.size} edits",
                            edits.map(::describe),
                            dataOf("undone" to encoded(edits)),
                        )
                    },
            ),
    )

private fun summarize(group: EditGroup) =
    EditSummary(
        id = group.id,
        caller = group.caller,
        action = group.action,
        time = group.time.truncatedTo(ChronoUnit.SECONDS).toString(),
        blocks = group.blocks,
        entities = group.entities.size,
        dimensions =
            (group.snapshots.map { it.dimension } + group.entities.map { it.dimension })
                .map { it.identifier().toString() }
                .distinct(),
    )

private fun describe(edit: EditSummary): String =
    "#${edit.id} ${edit.action} by ${edit.caller} at ${edit.time}: ${edit.blocks} blocks and " +
        "${edit.entities} entities in ${edit.dimensions.joinToString(", ")}"
