use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::format::{compact, describe_entity, done, to_json};
use crate::schemas::{Rotation, Vec3, dimension, entity_ref};
use crate::tool::{ToolResult, ToolSpec, require_fields, required};

#[derive(Deserialize, JsonSchema)]
pub struct MoveInput {
    action: Option<String>,
    /// Entity UUID.
    uuid: String,
    /// Target dimension for teleport.
    dimension: Option<String>,
    /// Required for teleport.
    position: Option<Vec3>,
    /// Required for rotate, optional for teleport.
    rotation: Option<Rotation>,
    /// Required for push.
    velocity: Option<Vec3>,
    /// push adds to the current velocity instead of replacing it.
    #[serde(default)]
    add: bool,
    /// Vehicle UUID. Required for mount.
    vehicle: Option<String>,
}

pub fn movement() -> ToolSpec<MoveInput> {
    ToolSpec {
        name: "move_entity",
        title: "Move entity",
        description: "teleport moves an entity, players included, also across dimensions. rotate \
            turns it. push sets its velocity in blocks per tick. mount puts it on a vehicle, and \
            dismount takes it off."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("teleport", Write, teleport),
            action!("rotate", Write, rotate),
            action!("push", Write, push),
            action!("mount", Write, mount),
            action!("dismount", Write, dismount),
        ],
    }
}

async fn teleport(context: Arc<Context>, input: MoveInput) -> ToolResult {
    require_fields(&[("position", input.position.is_some())])?;
    let request = proto::TeleportRequest {
        uuid: input.uuid,
        dimension: dimension(input.dimension),
        position: input.position.map(Into::into),
        rotation: input.rotation.map(Into::into),
    };
    let moved = context
        .clients
        .entity
        .clone()
        .teleport(request)
        .await?
        .into_inner();
    Ok(done(describe_entity(&compact(to_json(&moved)))))
}

async fn rotate(context: Arc<Context>, input: MoveInput) -> ToolResult {
    require_fields(&[("rotation", input.rotation.is_some())])?;
    let request = proto::SetRotationRequest {
        uuid: input.uuid,
        rotation: input.rotation.map(Into::into),
    };
    context.clients.entity.clone().set_rotation(request).await?;
    Ok(done("Entity rotated"))
}

async fn push(context: Arc<Context>, input: MoveInput) -> ToolResult {
    require_fields(&[("velocity", input.velocity.is_some())])?;
    let request = proto::SetVelocityRequest {
        uuid: input.uuid,
        velocity: input.velocity.map(Into::into),
        add: input.add,
    };
    context.clients.entity.clone().set_velocity(request).await?;
    Ok(done("Velocity set"))
}

async fn mount(context: Arc<Context>, input: MoveInput) -> ToolResult {
    let vehicle = required(input.vehicle, "vehicle")?;
    context
        .clients
        .entity
        .clone()
        .mount(proto::MountRequest {
            passenger: input.uuid,
            vehicle,
        })
        .await?;
    Ok(done("Mounted"))
}

async fn dismount(context: Arc<Context>, input: MoveInput) -> ToolResult {
    context
        .clients
        .entity
        .clone()
        .dismount(entity_ref(input.uuid))
        .await?;
    Ok(done("Dismounted"))
}
