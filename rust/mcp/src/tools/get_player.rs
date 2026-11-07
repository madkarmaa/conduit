use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Map, Value, json};

use crate::Context;
use crate::action;
use crate::format::{compact, done, json, lines, message, to_json};
use crate::schemas::player_ref;
use crate::tool::{ToolResult, ToolSpec};

#[derive(Deserialize, JsonSchema)]
pub struct GetInput {
    action: Option<String>,
    /// Player name or UUID.
    player: String,
    /// e.g. "mined" or "diamond".
    #[serde(default)]
    stats_filter: String,
}

pub fn get() -> ToolSpec<GetInput> {
    ToolSpec {
        name: "get_player",
        title: "Get player",
        description: "info, inventory and ender_chest read an online player. Inventory slots: 0-8 \
            hotbar, 9-35 main, 36-39 armor (feet, legs, chest, head), 40 offhand. stats reads the \
            statistics of any player, filtered by a substring. saved and saved_nbt read the saved \
            data of any player, also offline ones."
            .into(),
        default_action: Some("info"),
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("info", Read, info),
            action!("inventory", Read, inventory),
            action!("ender_chest", Read, ender_chest),
            action!("stats", Read, stats),
            action!("saved", Read, saved),
            action!("saved_nbt", Read, saved_nbt),
        ],
    }
}

async fn info(context: Arc<Context>, input: GetInput) -> ToolResult {
    Ok(message(
        &context
            .clients
            .player
            .clone()
            .get_player(player_ref(input.player))
            .await?
            .into_inner(),
    ))
}

async fn inventory(context: Arc<Context>, input: GetInput) -> ToolResult {
    let inventory = context
        .clients
        .player
        .clone()
        .get_inventory(player_ref(input.player))
        .await?
        .into_inner();
    let header = format!(
        "Selected hotbar slot {}; non-empty slots:",
        inventory.selected_slot
    );
    describe_inventory(header, &inventory)
}

async fn ender_chest(context: Arc<Context>, input: GetInput) -> ToolResult {
    let inventory = context
        .clients
        .player
        .clone()
        .get_ender_chest(player_ref(input.player))
        .await?
        .into_inner();
    describe_inventory("Non-empty ender chest slots:".into(), &inventory)
}

async fn stats(context: Arc<Context>, input: GetInput) -> ToolResult {
    let stats = context
        .clients
        .player
        .clone()
        .get_player_stats(player_ref(input.player))
        .await?
        .into_inner();
    let mut names: Vec<&String> = stats
        .stats
        .keys()
        .filter(|name| name.contains(&input.stats_filter))
        .collect();
    names.sort();
    let filtered: Map<String, Value> = names
        .into_iter()
        .map(|name| (name.clone(), json!(stats.stats[name])))
        .collect();
    Ok(json(Value::Object(filtered)))
}

async fn saved(context: Arc<Context>, input: GetInput) -> ToolResult {
    Ok(message(
        &context
            .clients
            .player
            .clone()
            .get_offline_player(player_ref(input.player))
            .await?
            .into_inner(),
    ))
}

async fn saved_nbt(context: Arc<Context>, input: GetInput) -> ToolResult {
    let nbt = context
        .clients
        .player
        .clone()
        .get_offline_player_nbt(player_ref(input.player))
        .await?
        .into_inner();
    Ok(done(nbt.snbt))
}

fn describe_inventory(header: String, inventory: &proto::Inventory) -> ToolResult {
    let rows = inventory
        .slots
        .iter()
        .map(|slot| {
            let item = slot.item.as_ref();
            let components = item.map_or("", |item| item.components.as_str());
            let extra = if components.is_empty() {
                String::new()
            } else {
                format!(" {components}")
            };
            format!(
                "{}: {} {}{extra}",
                slot.slot,
                item.map_or("undefined".to_owned(), |item| item.count.to_string()),
                item.map_or("undefined", |item| item.id.as_str()),
            )
        })
        .collect();
    let slots: Vec<Value> = inventory
        .slots
        .iter()
        .map(|slot| compact(to_json(slot)))
        .collect();
    Ok(lines(header, rows, json!({ "slots": slots })))
}
