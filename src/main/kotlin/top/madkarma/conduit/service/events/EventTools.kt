package top.madkarma.conduit.service.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import top.madkarma.conduit.game.EntityInfo
import top.madkarma.conduit.game.GAME_JSON
import top.madkarma.conduit.mcp.*
import top.madkarma.conduit.service.entity.describeEntity

private val EVENT_NAMES = FEED_TYPES.map { it.id }

// Damage, death and attack events carry the full entity, and one line is enough to read them.
private val ENTITY_FIELDS = setOf("entity", "target")

val EVENTS =
    ToolDefinition(
        name = "events",
        title = "Game events",
        description =
            "wait returns game events newer than a cursor, waiting up to timeout_seconds for one to " +
                "happen. Pass the returned next cursor as after to continue where the last call " +
                "stopped. Events are recorded from the first call on. Event types: " +
                "${EVENT_NAMES.joinToString(", ")}.",
        input =
            input {
                optional(
                    "after",
                    integerSchema("Cursor from the previous call.", min = 0, default = 0),
                )
                optional(
                    "types",
                    arraySchema(
                        enumSchema(EVENT_NAMES),
                        "Empty means every type.",
                        default = EMPTY_ARRAY,
                    ),
                )
                optional(
                    "players",
                    arraySchema(
                        stringSchema(),
                        "Only events about these players or entities, by name or UUID.",
                        default = EMPTY_ARRAY,
                    ),
                )
                optional("limit", integerSchema(min = 1, max = 200, default = 50))
                optional("timeout_seconds", numberSchema(min = 0.0, max = 60.0, default = 10.0))
            },
        actions =
            mapOf(
                "wait" to
                    AsyncAction(Access.READ) { input, _ ->
                        val query =
                            EventQuery(
                                after = input.long("after"),
                                types = input.strings("types").toSet(),
                                players = input.strings("players"),
                                limit = input.int("limit"),
                                timeoutMs = (input.double("timeout_seconds") * 1000).toLong(),
                            )
                        describePage(EventFeed.poll(query))
                    },
            ),
    )

private fun describePage(page: EventPage): Reply {
    val expired = if (page.expired > 0) "; ${page.expired} older events expired unread" else ""
    val rows =
        page.events.map { (cursor, event) ->
            "#$cursor tick ${event.tick} ${event.type.id} ${compact(summarizeEntities(event.payload)) ?: "{}"}"
        }
    val events =
        page.events.map { (cursor, event) ->
            dataOf(
                "cursor" to JsonPrimitive(cursor),
                "type" to JsonPrimitive(event.type.id),
                "tick" to JsonPrimitive(event.tick),
                "payload" to (compact(event.payload) ?: JsonObject(emptyMap())),
            )
        }
    val data =
        dataOf(
            "nextCursor" to JsonPrimitive(page.nextCursor),
            "expired" to JsonPrimitive(page.expired),
            "events" to JsonArray(events),
        )

    return lines("${page.events.size} events, next cursor ${page.nextCursor}$expired", rows, data)
}

private fun summarizeEntities(payload: JsonObject): JsonObject =
    JsonObject(
        payload.mapValues { (key, value) -> if (key in ENTITY_FIELDS) describe(value) else value },
    )

private fun describe(entity: JsonElement): JsonElement =
    JsonPrimitive(describeEntity(GAME_JSON.decodeFromJsonElement<EntityInfo>(entity)))
