use serde_json::Value;

use crate::tool::{Reply, ToolError, invalid_input};

pub struct ToolPolicy {
    pub read_only: bool,
    /// Only these tools, or all when empty.
    pub allowed_tools: Vec<String>,
    pub denied_tools: Vec<String>,
    /// Names and UUIDs of the one player whose data the tools may use, or none for every player.
    pub player: Vec<String>,
}

impl ToolPolicy {
    pub fn from_env() -> Self {
        Self {
            read_only: std::env::var("CONDUIT_MCP_READ_ONLY").is_ok_and(|value| value == "true"),
            allowed_tools: list("CONDUIT_MCP_TOOLS"),
            denied_tools: list("CONDUIT_MCP_DENY_TOOLS"),
            player: list("CONDUIT_MCP_PLAYER"),
        }
    }
}

fn list(variable: &str) -> Vec<String> {
    std::env::var(variable)
        .unwrap_or_default()
        .split(',')
        .map(str::trim)
        .filter(|item| !item.is_empty())
        .map(str::to_owned)
        .collect()
}

/// IP addresses identify a player outside the game.
const PRIVATE_KEYS: &[&str] = &["ipAddress"];

pub fn offers(policy: &ToolPolicy, name: &str, sees_other_players: bool) -> bool {
    if !policy.allowed_tools.is_empty() && !policy.allowed_tools.iter().any(|tool| tool == name) {
        return false;
    }
    if policy.denied_tools.iter().any(|tool| tool == name) {
        return false;
    }
    policy.player.is_empty() || !sees_other_players
}

/// A server limited to one player only accepts that player in player arguments. An empty players
/// filter would mean every player, so it has to name them.
pub fn check_players(input: &Value, identities: &[String]) -> Result<(), ToolError> {
    if identities.is_empty() {
        return Ok(());
    }

    let players = input.get("players").and_then(Value::as_array);
    let mut named: Vec<&Value> = players
        .map(|list| list.iter().collect())
        .unwrap_or_default();
    if let Some(player) = input.get("player") {
        named.insert(0, player);
    }
    let allowed = |name: &&Value| {
        name.as_str().is_some_and(|name| {
            identities
                .iter()
                .any(|identity| identity.eq_ignore_ascii_case(name))
        })
    };

    if players.is_some_and(Vec::is_empty) {
        return Err(invalid_input(format!("Set players to {}", identities[0])));
    }
    if !named.iter().all(allowed) {
        return Err(invalid_input(format!(
            "Only the data of {} is available",
            identities[0]
        )));
    }
    Ok(())
}

pub fn redact(reply: Reply) -> Reply {
    let Some(data) = reply.data else { return reply };

    let mut value = Value::Object(data);
    remove_keys(&mut value);
    let Value::Object(data) = value else {
        unreachable!()
    };
    let leaks = PRIVATE_KEYS
        .iter()
        .any(|key| reply.text.contains(&format!("\"{key}\"")));
    let text = if leaks {
        serde_json::to_string(&data).unwrap_or_default()
    } else {
        reply.text
    };
    Reply {
        text,
        data: Some(data),
    }
}

fn remove_keys(value: &mut Value) {
    match value {
        Value::Object(map) => {
            map.retain(|key, _| !PRIVATE_KEYS.contains(&key.as_str()));
            map.values_mut().for_each(remove_keys);
        }
        Value::Array(items) => items.iter_mut().for_each(remove_keys),
        _ => {}
    }
}
