use std::sync::Arc;

use conduit_client::{Empty, proto};
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::json;

use crate::Context;
use crate::action;
use crate::format::{json, to_json};
use crate::schemas::dimension;
use crate::tool::{ToolResult, ToolSpec};

#[derive(Deserialize, JsonSchema)]
pub struct DimensionInput {
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
}

pub fn world_info() -> ToolSpec<DimensionInput> {
    ToolSpec {
        name: "world_info",
        title: "World info",
        description: "Time, weather, world spawn and world border of a dimension. A day is 24000 \
            ticks, with sunrise at 0 and noon at 6000."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("get", Read, read_world)],
    }
}

async fn read_world(context: Arc<Context>, input: DimensionInput) -> ToolResult {
    let mut world = context.clients.world.clone();
    let request = proto::DimensionRef {
        dimension: dimension(input.dimension),
    };
    let time = world.get_time(request.clone()).await?.into_inner();
    let weather = world.get_weather(request.clone()).await?.into_inner();
    let spawn = world.get_spawn(Empty {}).await?.into_inner();
    let border = world.get_world_border(request).await?.into_inner();
    Ok(json(json!({
        "time": to_json(&time),
        "weather": to_json(&weather),
        "spawn": to_json(&spawn),
        "worldBorder": to_json(&border),
    })))
}
