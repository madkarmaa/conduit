use std::sync::Arc;

use conduit_client::Empty;
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Value, json};

use crate::Context;
use crate::action;
use crate::format::{compact, iso_time, lines, position, to_json};
use crate::tool::{ToolResult, ToolSpec};

#[derive(Deserialize, JsonSchema)]
pub struct ListInput {
    action: Option<String>,
}

pub fn list() -> ToolSpec<ListInput> {
    ToolSpec {
        name: "list_players",
        title: "List players",
        description: "online: online players with game mode, position, health and food. known: \
            every player with saved data, also offline ones."
            .into(),
        default_action: Some("online"),
        sees_other_players: true,
        meta: None,
        actions: vec![
            action!("online", Read, online),
            action!("known", Read, known),
        ],
    }
}

async fn online(context: Arc<Context>, _: ListInput) -> ToolResult {
    let list = context
        .clients
        .player
        .clone()
        .list_players(Empty {})
        .await?
        .into_inner();
    let players: Vec<Value> = list
        .players
        .iter()
        .map(|player| compact(to_json(player)))
        .collect();
    let rows = list
        .players
        .iter()
        .map(|player| {
            let entity = player.entity.as_ref();
            let position_json = entity
                .and_then(|entity| entity.position.as_ref())
                .map(to_json);
            format!(
                "{} {} {} {} {} {}hp food {}",
                player.name,
                entity.map_or("undefined", |entity| entity.uuid.as_str()),
                player.game_mode,
                entity.map_or("undefined", |entity| entity.dimension.as_str()),
                position(position_json.as_ref()),
                entity
                    .and_then(|entity| entity.health)
                    .map_or("undefined".to_owned(), |health| crate::format::number(
                        f64::from(health)
                    )),
                player.food_level,
            )
        })
        .collect();
    Ok(lines(
        format!("{} players online", list.players.len()),
        rows,
        json!({ "players": players }),
    ))
}

async fn known(context: Arc<Context>, _: ListInput) -> ToolResult {
    let list = context
        .clients
        .player
        .clone()
        .list_known_players(Empty {})
        .await?
        .into_inner();
    let rows = list
        .players
        .iter()
        .map(|known| {
            let name = if known.name.is_empty() {
                "?"
            } else {
                &known.name
            };
            let status = if known.online {
                " online".to_owned()
            } else {
                format!(" last played {}", iso_time(known.last_played))
            };
            format!("{name} {}{status}", known.uuid)
        })
        .collect();
    let data = json!({ "players": to_json(&list.players) });
    Ok(lines(
        format!("{} known players", list.players.len()),
        rows,
        data,
    ))
}
