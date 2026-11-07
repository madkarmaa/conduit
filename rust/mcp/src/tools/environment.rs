use std::sync::Arc;

use conduit_client::{Empty, proto};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::Context;
use crate::action;
use crate::format::{done, json, to_json};
use crate::schemas::{Vec3, dimension};
use crate::tool::{ToolResult, ToolSpec, require_fields};

#[derive(Deserialize, JsonSchema)]
pub struct RulesInput {
    action: Option<String>,
    /// Rule name, e.g. "advance_time".
    name: Option<String>,
    /// New value, e.g. "false" or "3".
    value: Option<String>,
}

#[derive(Deserialize, JsonSchema)]
pub struct BorderInput {
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    center_x: Option<f64>,
    center_z: Option<f64>,
    /// Side length in blocks.
    #[schemars(range(min = 1))]
    size: Option<f64>,
    /// Time to grow or shrink to size. 0 changes it instantly.
    #[serde(default)]
    #[schemars(range(min = 0))]
    transition_ms: i64,
    #[schemars(range(min = 0))]
    damage_per_block: Option<f64>,
    #[schemars(range(min = 0))]
    damage_safe_zone: Option<f64>,
    #[schemars(range(min = 0))]
    warning_blocks: Option<i32>,
    #[schemars(range(min = 0))]
    warning_time_ticks: Option<i32>,
}

#[derive(Deserialize, Serialize, JsonSchema)]
#[allow(clippy::upper_case_acronyms)]
enum Interaction {
    NONE,
    BLOCK,
    MOB,
    TNT,
    TRIGGER,
}

fn tnt() -> Interaction {
    Interaction::TNT
}

fn tnt_power() -> f32 {
    4.0
}

#[derive(Deserialize, JsonSchema)]
pub struct ExplodeInput {
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    position: Vec3,
    #[serde(default = "tnt_power")]
    #[schemars(range(min = 0))]
    power: f32,
    #[serde(default)]
    fire: bool,
    /// How the explosion affects blocks. NONE breaks no blocks.
    #[serde(default = "tnt")]
    interaction: Interaction,
}

pub fn game_rules() -> ToolSpec<RulesInput> {
    ToolSpec {
        name: "game_rules",
        title: "Game rules",
        description: "Lists every game rule with its value, or sets one.".into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("list", Read, list_rules),
            action!("set", Destructive, set_rule),
        ],
    }
}

async fn list_rules(context: Arc<Context>, _: RulesInput) -> ToolResult {
    let rules = context
        .clients
        .world
        .clone()
        .get_game_rules(Empty {})
        .await?
        .into_inner();
    Ok(json(to_json(&rules.rules)))
}

async fn set_rule(context: Arc<Context>, input: RulesInput) -> ToolResult {
    require_fields(&[
        ("name", input.name.is_some()),
        ("value", input.value.is_some()),
    ])?;
    let (name, value) = (
        input.name.unwrap_or_default(),
        input.value.unwrap_or_default(),
    );
    let text = format!("{name} = {value}");
    context
        .clients
        .world
        .clone()
        .set_game_rule(proto::SetGameRuleRequest { name, value })
        .await?;
    Ok(done(text))
}

pub fn world_border() -> ToolSpec<BorderInput> {
    ToolSpec {
        name: "world_border",
        title: "Set world border",
        description: "Changes a dimension's world border. Fields left out stay unchanged. \
            world_info reads the current border."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("set", Destructive, set_border)],
    }
}

async fn set_border(context: Arc<Context>, input: BorderInput) -> ToolResult {
    let request = proto::SetWorldBorderRequest {
        dimension: dimension(input.dimension),
        center_x: input.center_x,
        center_z: input.center_z,
        size: input.size,
        transition_ms: input.transition_ms,
        damage_per_block: input.damage_per_block,
        damage_safe_zone: input.damage_safe_zone,
        warning_blocks: input.warning_blocks,
        warning_time_ticks: input.warning_time_ticks,
    };
    context
        .clients
        .world
        .clone()
        .set_world_border(request)
        .await?;
    Ok(done("World border updated"))
}

pub fn explode() -> ToolSpec<ExplodeInput> {
    ToolSpec {
        name: "explode",
        title: "Explode",
        description: "Creates an explosion. TNT has power 4, a creeper 3.".into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("explode", Destructive, create_explosion)],
    }
}

async fn create_explosion(context: Arc<Context>, input: ExplodeInput) -> ToolResult {
    let interaction = match input.interaction {
        Interaction::NONE => "NONE",
        Interaction::BLOCK => "BLOCK",
        Interaction::MOB => "MOB",
        Interaction::TNT => "TNT",
        Interaction::TRIGGER => "TRIGGER",
    };
    let request = proto::ExplodeRequest {
        dimension: dimension(input.dimension),
        position: Some(input.position.into()),
        power: input.power,
        fire: input.fire,
        interaction: interaction.into(),
    };
    context.clients.world.clone().explode(request).await?;
    Ok(done("Exploded"))
}
