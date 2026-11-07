use std::sync::Arc;

use conduit_client::{Empty, proto};
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::format::{done, lines, message, to_json};
use crate::schemas::{Rotation, Vec3};
use crate::tool::{ToolResult, ToolSpec, require_fields};

#[derive(Deserialize, JsonSchema)]
pub struct CommandInput {
    /// Command with or without the slash, e.g. "time set day".
    command: String,
    /// Player name or UUID, or entity UUID to run as.
    #[serde(rename = "as")]
    run_as: Option<String>,
    dimension: Option<String>,
    position: Option<Vec3>,
    rotation: Option<Rotation>,
    #[serde(default = "owner")]
    #[schemars(range(min = 0, max = 4))]
    permission_level: i32,
}

fn owner() -> i32 {
    4
}

#[derive(Deserialize, JsonSchema)]
pub struct AdminInput {
    action: Option<String>,
    /// save waits until everything is written.
    #[serde(default)]
    flush: bool,
    /// Appended to the backup file name.
    #[schemars(length(max = 64))]
    name: Option<String>,
    /// Datapack id for set_datapack, e.g. "file/my_pack".
    id: Option<String>,
    /// Required for set_datapack.
    enabled: Option<bool>,
}

pub fn command() -> ToolSpec<CommandInput> {
    ToolSpec {
        name: "run_command",
        title: "Run command",
        description: "Runs any command like the server console, and returns whether it succeeded, \
            its result value and its feedback. Can run as a player or entity and at a position."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("run", Destructive, run_command)],
    }
}

async fn run_command(context: Arc<Context>, input: CommandInput) -> ToolResult {
    let request = proto::ExecuteCommandRequest {
        command: input.command,
        r#as: input.run_as,
        dimension: input.dimension,
        position: input.position.map(Into::into),
        rotation: input.rotation.map(Into::into),
        permission_level: Some(input.permission_level),
    };
    let response = context
        .clients
        .server
        .clone()
        .execute_command(request)
        .await?
        .into_inner();
    let header = format!(
        "{}, result {}",
        if response.success {
            "Succeeded"
        } else {
            "Failed"
        },
        response.result
    );
    let data = to_json(&response);
    Ok(lines(header, response.output, data))
}

pub fn admin() -> ToolSpec<AdminInput> {
    ToolSpec {
        name: "server_admin",
        title: "Server administration",
        description: "save writes worlds and players to disk. backup saves the world and zips it \
            while the server keeps running. set_datapack enables or disables a datapack and \
            reloads the data. stop saves and stops the server."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("save", Write, save),
            action!("backup", Write, backup),
            action!("set_datapack", Destructive, set_datapack),
            action!("stop", Destructive, stop),
        ],
    }
}

async fn save(context: Arc<Context>, input: AdminInput) -> ToolResult {
    context
        .clients
        .server
        .clone()
        .save(proto::SaveRequest { flush: input.flush })
        .await?;
    Ok(done("Saved"))
}

async fn backup(context: Arc<Context>, input: AdminInput) -> ToolResult {
    let request = proto::BackupRequest { name: input.name };
    Ok(message(
        &context
            .clients
            .server
            .clone()
            .backup(request)
            .await?
            .into_inner(),
    ))
}

async fn set_datapack(context: Arc<Context>, input: AdminInput) -> ToolResult {
    require_fields(&[
        ("id", input.id.is_some()),
        ("enabled", input.enabled.is_some()),
    ])?;
    let request = proto::SetDatapackEnabledRequest {
        id: input.id.unwrap_or_default(),
        enabled: input.enabled.unwrap_or_default(),
    };
    Ok(message(
        &context
            .clients
            .server
            .clone()
            .set_datapack_enabled(request)
            .await?
            .into_inner(),
    ))
}

async fn stop(context: Arc<Context>, _: AdminInput) -> ToolResult {
    context.clients.server.clone().stop(Empty {}).await?;
    Ok(done("Server stopping"))
}
