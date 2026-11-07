use std::time::Duration;

use serde_json::Value;

use conduit_client::proto::EventType;

use crate::events::BufferedEvent;

pub struct EventQuery {
    pub after: u64,
    pub names: Vec<String>,
    pub players: Vec<String>,
    pub limit: usize,
    pub timeout: Duration,
}

pub fn matches(event: &BufferedEvent, query: &EventQuery) -> bool {
    if event.cursor <= query.after {
        return false;
    }
    if event.name == "events_dropped" {
        return true;
    }
    if !query.names.is_empty() && !query.names.contains(&event.name) {
        return false;
    }
    query.players.is_empty() || involves_player(&event.payload, &query.players)
}

fn involves_player(payload: &Value, players: &[String]) -> bool {
    let entity = payload.get("entity");
    let candidates = [
        payload.get("player"),
        payload.get("playerUuid"),
        entity.and_then(|entity| entity.get("name")),
        entity.and_then(|entity| entity.get("uuid")),
    ];
    candidates
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .any(|candidate| {
            players
                .iter()
                .any(|player| player.eq_ignore_ascii_case(candidate))
        })
}

/// Ticks, movement, chunk loading and unloaded entities would flood the buffer, so they are left
/// out.
pub const SUBSCRIBED_TYPES: &[EventType] = &[
    EventType::Chat,
    EventType::PlayerJoin,
    EventType::PlayerLeave,
    EventType::PlayerRespawn,
    EventType::BlockBreak,
    EventType::BlockPlace,
    EventType::BlockUse,
    EventType::EntityDamage,
    EventType::EntityDeath,
    EventType::EntityAttack,
    EventType::PlayerChangeDimension,
    EventType::Command,
    EventType::ServerStarted,
    EventType::ServerStopping,
    EventType::EntitySpawn,
    EventType::Advancement,
    EventType::InventoryChange,
    EventType::ItemPickup,
    EventType::ItemDrop,
    EventType::ItemCraft,
    EventType::ItemSmelt,
    EventType::Trade,
];

pub fn event_names() -> Vec<String> {
    SUBSCRIBED_TYPES
        .iter()
        .map(|kind| {
            kind.as_str_name()
                .trim_start_matches("EVENT_TYPE_")
                .to_lowercase()
        })
        .collect()
}

pub fn snake_case(name: &str) -> String {
    let mut snake = String::with_capacity(name.len() + 4);
    for character in name.chars() {
        if character.is_ascii_uppercase() {
            snake.push('_');
            snake.push(character.to_ascii_lowercase());
        } else {
            snake.push(character);
        }
    }
    snake
}
