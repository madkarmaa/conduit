use std::sync::Arc;
use std::time::Duration;

use schemars::{JsonSchema, Schema, SchemaGenerator, json_schema};
use serde::Deserialize;
use serde_json::{Value, json};

use crate::Context;
use crate::action;
use crate::event_types::{EventQuery, event_names};
use crate::format::{compact, describe_entity, lines};
use crate::tool::{ToolResult, ToolSpec, invalid_input};

/// Damage, death and attack events carry the full entity. One line is enough to read them.
const ENTITY_FIELDS: &[&str] = &["entity", "target"];

fn fifty() -> usize {
    50
}

fn ten() -> f64 {
    10.0
}

#[derive(Deserialize, JsonSchema)]
pub struct WaitInput {
    /// Cursor from the previous call.
    #[serde(default)]
    after: u64,
    #[serde(default)]
    #[schemars(schema_with = "event_types")]
    types: Vec<String>,
    /// Only events about these players or entities, by name or UUID.
    #[serde(default)]
    players: Vec<String>,
    #[serde(default = "fifty")]
    #[schemars(range(min = 1, max = 200))]
    limit: usize,
    #[serde(default = "ten")]
    #[schemars(range(min = 0, max = 60))]
    timeout_seconds: f64,
}

fn event_types(_: &mut SchemaGenerator) -> Schema {
    json_schema!({
        "type": "array",
        "items": { "type": "string", "enum": event_names() },
        "default": [],
        "description": "Empty means every type.",
    })
}

pub fn wait_for_events() -> ToolSpec<WaitInput> {
    ToolSpec {
        name: "wait_for_events",
        title: "Wait for events",
        description: format!(
            "Returns game events newer than a cursor, waiting up to timeout_seconds for one to \
             happen. Pass the returned next cursor as after to continue where the last call \
             stopped. Events are recorded from the first call on. Event types: {}. events_dropped \
             reports events the server lost.",
            event_names().join(", ")
        ),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("wait", Read, wait)],
    }
}

async fn wait(context: Arc<Context>, input: WaitInput) -> ToolResult {
    let known = event_names();
    if let Some(unknown) = input.types.iter().find(|name| !known.contains(name)) {
        return Err(invalid_input(format!("Unknown event type {unknown}")));
    }
    let query = EventQuery {
        after: input.after,
        names: input.types,
        players: input.players,
        limit: input.limit.clamp(1, 200),
        timeout: Duration::from_secs_f64(input.timeout_seconds.clamp(0.0, 60.0)),
    };
    let page = context.feed.poll(query).await?;

    let rows = page
        .events
        .iter()
        .map(|event| {
            format!(
                "#{} tick {} {} {}",
                event.cursor,
                event.tick,
                event.name,
                summarize_entities(compact(event.payload.clone()))
            )
        })
        .collect();
    let expired = if page.expired > 0 {
        format!("; {} older events expired unread", page.expired)
    } else {
        String::new()
    };
    let events: Vec<Value> = page
        .events
        .iter()
        .map(|event| {
            json!({ "cursor": event.cursor, "type": event.name, "tick": event.tick, "payload": compact(event.payload.clone()) })
        })
        .collect();
    let data = json!({ "nextCursor": page.next_cursor, "expired": page.expired, "events": events });
    Ok(lines(
        format!(
            "{} events, next cursor {}{expired}",
            page.events.len(),
            page.next_cursor
        ),
        rows,
        data,
    ))
}

fn summarize_entities(mut payload: Value) -> Value {
    if let Value::Object(fields) = &mut payload {
        for key in ENTITY_FIELDS {
            if let Some(entity) = fields.get_mut(*key)
                && entity.is_object()
            {
                *entity = Value::String(describe_entity(entity));
            }
        }
    }
    payload
}
