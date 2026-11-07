use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::format::done;
use crate::schemas::to_text;
use crate::tool::{ToolResult, ToolSpec, require_fields, required};

#[derive(Clone, Copy, Deserialize, JsonSchema)]
#[serde(rename_all = "lowercase")]
enum GameMode {
    Survival,
    Creative,
    Adventure,
    Spectator,
}

fn kick_reason() -> String {
    "Kicked by an operator".into()
}

#[derive(Deserialize, JsonSchema)]
pub struct EditInput {
    action: Option<String>,
    /// Player name or UUID.
    player: String,
    game_mode: Option<GameMode>,
    #[schemars(range(min = 0, max = 20))]
    food_level: Option<i32>,
    #[schemars(range(min = 0))]
    saturation: Option<f32>,
    #[schemars(range(min = 0))]
    levels: Option<i32>,
    #[schemars(range(min = 0))]
    points: Option<i32>,
    /// Required for operator.
    operator: Option<bool>,
    /// Kick message.
    #[serde(default = "kick_reason")]
    reason: String,
}

pub fn edit() -> ToolSpec<EditInput> {
    ToolSpec {
        name: "edit_player",
        title: "Edit player",
        description: "Changes an online player: game_mode, food (level 0-20 and saturation), \
            experience (levels and points within the level), operator status, or kick disconnects \
            them."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("game_mode", Write, game_mode),
            action!("food", Write, food),
            action!("experience", Write, experience),
            action!("operator", Destructive, operator),
            action!("kick", Destructive, kick),
        ],
    }
}

async fn game_mode(context: Arc<Context>, input: EditInput) -> ToolResult {
    let mode = required(input.game_mode, "game_mode")?;
    let name = match mode {
        GameMode::Survival => "survival",
        GameMode::Creative => "creative",
        GameMode::Adventure => "adventure",
        GameMode::Spectator => "spectator",
    };
    let request = proto::SetGameModeRequest {
        player: input.player,
        game_mode: name.into(),
    };
    context
        .clients
        .player
        .clone()
        .set_game_mode(request)
        .await?;
    Ok(done(format!("Game mode set to {name}")))
}

async fn food(context: Arc<Context>, input: EditInput) -> ToolResult {
    let request = proto::SetFoodRequest {
        player: input.player,
        food_level: input.food_level,
        saturation: input.saturation,
    };
    context.clients.player.clone().set_food(request).await?;
    Ok(done("Food updated"))
}

async fn experience(context: Arc<Context>, input: EditInput) -> ToolResult {
    let request = proto::SetExperienceRequest {
        player: input.player,
        levels: input.levels,
        points: input.points,
    };
    context
        .clients
        .player
        .clone()
        .set_experience(request)
        .await?;
    Ok(done("Experience updated"))
}

async fn operator(context: Arc<Context>, input: EditInput) -> ToolResult {
    require_fields(&[("operator", input.operator.is_some())])?;
    let operator = input.operator.unwrap_or_default();
    context
        .clients
        .player
        .clone()
        .set_operator(proto::SetOperatorRequest {
            player: input.player,
            operator,
        })
        .await?;
    Ok(done(if operator {
        "Player is now operator"
    } else {
        "Operator removed"
    }))
}

async fn kick(context: Arc<Context>, input: EditInput) -> ToolResult {
    let request = proto::KickRequest {
        player: input.player,
        reason: Some(to_text(input.reason)),
    };
    context.clients.player.clone().kick(request).await?;
    Ok(done("Player kicked"))
}
