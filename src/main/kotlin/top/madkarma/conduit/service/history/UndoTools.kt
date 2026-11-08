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
    val dimensions: List<String>,
)

val UNDO =
    ToolDefinition(
        name = "undo",
        title = "Undo edits",
        description =
            "Block changes from edit_blocks, edit_world explode, structures place and copy, and " +
                "containers edited with inventory are recorded, one edit per call. list shows " +
                "the recent edits, newest first. undo " +
                "restores the blocks and block entities of the given edits, or of your newest " +
                "one. It does not bring back entities or dropped items, and it cannot undo " +
                "run_command, fire spread, falling blocks or anything before a server restart.",
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
        dimensions = group.snapshots.map { it.dimension.identifier().toString() }.distinct(),
    )

private fun describe(edit: EditSummary): String =
    "#${edit.id} ${edit.action} by ${edit.caller} at ${edit.time}: ${edit.blocks} blocks in " +
        edit.dimensions.joinToString(", ")
