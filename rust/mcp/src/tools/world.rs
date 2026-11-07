use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use super::terrain::chunk_of;
use crate::Context;
use crate::action;
use crate::format::done;
use crate::schemas::{BlockPos, dimension};
use crate::tool::{ToolResult, ToolSpec, invalid_input, require_fields, required};

#[derive(Deserialize, JsonSchema)]
#[serde(rename_all = "lowercase")]
enum Preset {
    Day,
    Noon,
    Night,
    Midnight,
}

#[derive(Clone, Copy, Deserialize, JsonSchema)]
#[serde(rename_all = "lowercase")]
enum Weather {
    Clear,
    Rain,
    Thunder,
}

#[derive(Deserialize, JsonSchema)]
pub struct SettingsInput {
    action: Option<String>,
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    /// Absolute time of day in ticks.
    #[schemars(range(min = 0))]
    time: Option<i64>,
    /// Ticks to add to the current time.
    add: Option<i64>,
    preset: Option<Preset>,
    weather: Option<Weather>,
    /// 0 means a random duration.
    #[serde(default)]
    #[schemars(range(min = 0))]
    duration_ticks: i32,
    /// Required for spawn.
    pos: Option<BlockPos>,
    #[serde(default)]
    yaw: f32,
    #[serde(default)]
    pitch: f32,
    /// Block x of the column. Required for height and chunks.
    x: Option<i32>,
    /// Block z of the column. Required for height and chunks.
    z: Option<i32>,
    /// Required for force_chunk.
    forced: Option<bool>,
}

pub fn world_settings() -> ToolSpec<SettingsInput> {
    ToolSpec {
        name: "world_settings",
        title: "World settings",
        description: "time sets the time of day from exactly one of time, add or preset. weather \
            sets the weather. spawn sets the world spawn. force_chunk force-loads or releases the \
            chunk that contains a column, so entities can spawn or move there."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("time", Write, set_time),
            action!("weather", Write, set_weather),
            action!("spawn", Write, set_spawn),
            action!("force_chunk", Write, force_chunk),
        ],
    }
}

async fn set_time(context: Arc<Context>, input: SettingsInput) -> ToolResult {
    use proto::set_time_request::Value;
    let value = match (input.time, input.add, input.preset) {
        (Some(time), None, None) => Value::Time(time),
        (None, Some(add), None) => Value::Add(add),
        (None, None, Some(preset)) => Value::Preset(
            match preset {
                Preset::Day => "day",
                Preset::Noon => "noon",
                Preset::Night => "night",
                Preset::Midnight => "midnight",
            }
            .into(),
        ),
        _ => return Err(invalid_input("Set exactly one of time, add or preset")),
    };
    let request = proto::SetTimeRequest {
        dimension: dimension(input.dimension),
        value: Some(value),
    };
    context.clients.world.clone().set_time(request).await?;
    Ok(done("Time set"))
}

async fn set_weather(context: Arc<Context>, input: SettingsInput) -> ToolResult {
    use proto::set_weather_request::Weather as Kind;
    let weather = required(input.weather, "weather")?;
    let (kind, name) = match weather {
        Weather::Clear => (Kind::Clear, "clear"),
        Weather::Rain => (Kind::Rain, "rain"),
        Weather::Thunder => (Kind::Thunder, "thunder"),
    };
    let request = proto::SetWeatherRequest {
        weather: kind as i32,
        duration: input.duration_ticks,
    };
    context.clients.world.clone().set_weather(request).await?;
    Ok(done(format!("Weather set to {name}")))
}

async fn set_spawn(context: Arc<Context>, input: SettingsInput) -> ToolResult {
    require_fields(&[("pos", input.pos.is_some())])?;
    let request = proto::SpawnPoint {
        dimension: dimension(input.dimension),
        pos: input.pos.map(Into::into),
        yaw: input.yaw,
        pitch: input.pitch,
    };
    context.clients.world.clone().set_spawn(request).await?;
    Ok(done("Spawn point set"))
}

async fn force_chunk(context: Arc<Context>, input: SettingsInput) -> ToolResult {
    require_fields(&[
        ("x", input.x.is_some()),
        ("z", input.z.is_some()),
        ("forced", input.forced.is_some()),
    ])?;
    let forced = input.forced.unwrap_or_default();
    let chunk = chunk_of(
        input.dimension,
        input.x.unwrap_or_default(),
        input.z.unwrap_or_default(),
    );
    let request = proto::SetChunkForcedRequest {
        dimension: chunk.dimension,
        chunk_x: chunk.chunk_x,
        chunk_z: chunk.chunk_z,
        forced,
    };
    context
        .clients
        .world
        .clone()
        .set_chunk_forced(request)
        .await?;
    Ok(done(if forced {
        "Chunk force-loaded"
    } else {
        "Chunk released"
    }))
}
