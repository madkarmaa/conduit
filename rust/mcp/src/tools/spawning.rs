use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::format::{done, message};
use crate::schemas::{Rotation, Vec3, dimension, yes};
use crate::tool::{ToolResult, ToolSpec};

#[derive(Deserialize, JsonSchema)]
pub struct SpawnInput {
    /// Entity type, e.g. "minecraft:villager".
    #[serde(rename = "type")]
    kind: String,
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    position: Vec3,
    rotation: Option<Rotation>,
    /// Data applied on spawn, like the /summon nbt argument.
    snbt: Option<String>,
    /// Run natural spawn setup (equipment, variants).
    #[serde(default = "yes")]
    initialize: bool,
}

#[derive(Deserialize, JsonSchema)]
pub struct RemoveInput {
    /// Entity UUID.
    uuid: String,
    #[serde(default)]
    kill: bool,
}

pub fn spawn() -> ToolSpec<SpawnInput> {
    ToolSpec {
        name: "spawn_entity",
        title: "Spawn entity",
        description:
            "Spawns an entity in a loaded chunk and returns it. The force_chunk action of \
            world_settings loads chunks away from players."
                .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("spawn", Write, spawn_entity)],
    }
}

async fn spawn_entity(context: Arc<Context>, input: SpawnInput) -> ToolResult {
    let request = proto::SpawnEntityRequest {
        r#type: input.kind,
        dimension: dimension(input.dimension),
        position: Some(input.position.into()),
        rotation: input.rotation.map(Into::into),
        snbt: input.snbt.unwrap_or_default(),
        initialize: input.initialize,
    };
    Ok(message(
        &context
            .clients
            .entity
            .clone()
            .spawn_entity(request)
            .await?
            .into_inner(),
    ))
}

pub fn remove() -> ToolSpec<RemoveInput> {
    ToolSpec {
        name: "remove_entity",
        title: "Remove entity",
        description: "Removes an entity silently, or kills it so it drops loot.".into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("remove", Destructive, remove_entity)],
    }
}

async fn remove_entity(context: Arc<Context>, input: RemoveInput) -> ToolResult {
    let request = proto::RemoveEntityRequest {
        uuid: input.uuid,
        kill: input.kill,
    };
    context
        .clients
        .entity
        .clone()
        .remove_entity(request)
        .await?;
    Ok(done("Entity removed"))
}
