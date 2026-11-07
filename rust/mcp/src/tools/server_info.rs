use std::sync::Arc;

use conduit_client::Empty;
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Value, json};

use crate::Context;
use crate::action;
use crate::format::{iso_time, json, lines, message, to_json};
use crate::tool::{ToolResult, ToolSpec};

#[derive(Deserialize, JsonSchema)]
pub struct InfoInput {
    action: Option<String>,
}

pub fn info() -> ToolSpec<InfoInput> {
    ToolSpec {
        name: "server_info",
        title: "Server info",
        description: "summary reads the Minecraft and mod versions, players, seed, difficulty, \
            performance (TPS, MSPT, memory) and the dimensions with their height range. mods, \
            datapacks and backups list those."
            .into(),
        default_action: Some("summary"),
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("summary", Read, summary),
            action!("mods", Read, mods),
            action!("datapacks", Read, datapacks),
            action!("backups", Read, backups),
        ],
    }
}

async fn summary(context: Arc<Context>, _: InfoInput) -> ToolResult {
    let mut server = context.clients.server.clone();
    let info = server.get_info(Empty {}).await?.into_inner();
    let performance = server.get_performance(Empty {}).await?.into_inner();
    let dimensions = context
        .clients
        .world
        .clone()
        .list_dimensions(Empty {})
        .await?
        .into_inner();

    let mut summary = to_json(&info);
    if let Value::Object(fields) = &mut summary {
        fields.insert("mods".into(), json!([]));
        fields.insert("dimensions".into(), to_json(&dimensions.dimensions));
        fields.insert("performance".into(), to_json(&performance));
    }
    Ok(json(summary))
}

async fn mods(context: Arc<Context>, _: InfoInput) -> ToolResult {
    let info = context
        .clients
        .server
        .clone()
        .get_info(Empty {})
        .await?
        .into_inner();
    let data = json!({ "mods": info.mods });
    Ok(lines(
        format!("{} mods and libraries", info.mods.len()),
        info.mods,
        data,
    ))
}

async fn datapacks(context: Arc<Context>, _: InfoInput) -> ToolResult {
    Ok(message(
        &context
            .clients
            .server
            .clone()
            .list_datapacks(Empty {})
            .await?
            .into_inner(),
    ))
}

async fn backups(context: Arc<Context>, _: InfoInput) -> ToolResult {
    let list = context
        .clients
        .server
        .clone()
        .list_backups(Empty {})
        .await?
        .into_inner();
    let rows = list
        .backups
        .iter()
        .map(|backup| {
            format!(
                "{} {} bytes, created {}",
                backup.name,
                backup.size_bytes,
                iso_time(backup.created_timestamp_ms)
            )
        })
        .collect();
    Ok(lines(
        format!("{} backups", list.backups.len()),
        rows,
        json!({ "backups": to_json(&list.backups) }),
    ))
}
