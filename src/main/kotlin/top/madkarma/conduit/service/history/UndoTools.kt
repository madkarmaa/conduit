package top.madkarma.conduit.service.history

import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable
import top.madkarma.conduit.game.EditGroup
import top.madkarma.conduit.game.EditJournal
import top.madkarma.conduit.game.EntityChange
import top.madkarma.conduit.game.EntitySnapshot
import top.madkarma.conduit.game.UndoableChange
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.player.AdvancementChange
import top.madkarma.conduit.service.player.PlayerItems
import top.madkarma.conduit.service.player.PlayerSnapshot

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
    /** Players the edit changed. */
    val players: Int,
    val dimensions: List<String>,
)

val UNDO =
    ToolDefinition(
        name = "undo",
        title = "Undo edits",
        description =
            "Undoes edits of edit_blocks, edit_world explode, structures, inventory, " +
                "edit_entity, move_entity and edit_player, one edit per call, offline players " +
                "included. list shows the recent edits, newest first. undo restores the given " +
                "edits, or your newest, and overwrites what changed since. It cannot undo " +
                "run_command, dropped items, fire, falling blocks or edits before a restart.",
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
        entities = subjects(group) { it is EntityChange || it is EntitySnapshot },
        players =
            subjects(group) {
                it is PlayerItems || it is PlayerSnapshot || it is AdvancementChange
            },
        dimensions =
            (group.snapshots.map { it.dimension } +
                    group.changes.filterIsInstance<EntityChange>().map { it.dimension } +
                    group.changes.filterIsInstance<EntitySnapshot>().map { it.dimension })
                .map { it.identifier().toString() }
                .distinct(),
    )

private fun subjects(group: EditGroup, kind: (UndoableChange) -> Boolean): Int =
    group.changes.filter(kind).mapNotNull { it.subject }.distinct().size

private fun describe(edit: EditSummary): String =
    "#${edit.id} ${edit.action} by ${edit.caller} at ${edit.time}: ${edit.blocks} blocks, " +
        "${edit.entities} entities and ${edit.players} players" +
        edit.dimensions.joinToString(", ").let { if (it.isEmpty()) "" else " in $it" }
