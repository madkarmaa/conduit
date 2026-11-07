use std::sync::Arc;

use conduit_client::proto::{StructureMirror, StructureRotation};
use conduit_client::{Empty, proto};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::Context;
use crate::action;
use crate::format::{done, lines, message};
use crate::schemas::{BlockPos, default_limit, dimension, yes};
use crate::tool::{ToolResult, ToolSpec, require_fields};

#[derive(Default, Deserialize, Serialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
enum Rotation {
    #[default]
    None,
    #[serde(rename = "clockwise_90")]
    Clockwise90,
    #[serde(rename = "clockwise_180")]
    Clockwise180,
    #[serde(rename = "counterclockwise_90")]
    Counterclockwise90,
}

#[derive(Default, Deserialize, Serialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
enum Mirror {
    #[default]
    None,
    LeftRight,
    FrontBack,
}

fn whole() -> f32 {
    1.0
}

#[derive(Deserialize, JsonSchema)]
pub struct StructureInput {
    action: Option<String>,
    /// Template id for save and place, e.g. "builds:house".
    id: Option<String>,
    /// Substring of the id, for list.
    #[serde(default)]
    filter: String,
    /// Maximum results to return.
    #[serde(default = "default_limit")]
    #[schemars(range(min = 1, max = 1000))]
    limit: i32,
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    /// Region corner for save.
    from: Option<BlockPos>,
    /// Opposite region corner for save.
    to: Option<BlockPos>,
    /// Required for place.
    origin: Option<BlockPos>,
    #[serde(default)]
    rotation: Rotation,
    #[serde(default)]
    mirror: Mirror,
    #[serde(default = "yes")]
    include_entities: bool,
    /// Chance each block is placed.
    #[serde(default = "whole")]
    #[schemars(range(min = 0, max = 1))]
    integrity: f32,
    /// For save: block left out, e.g. "minecraft:structure_void".
    #[serde(default)]
    ignore_block: String,
}

pub fn structures() -> ToolSpec<StructureInput> {
    ToolSpec {
        name: "structures",
        title: "Structures",
        description:
            "Structure templates, the same ones structure blocks and /place template use. \
            list finds saved and datapack templates, including vanilla ones such as \
            \"minecraft:village/plains/houses/plains_small_house_1\". save copies a region into a \
            template. place pastes a template with its lowest corner at origin."
                .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("list", Read, list),
            action!("save", Write, save),
            action!("place", Destructive, place),
        ],
    }
}

async fn list(context: Arc<Context>, input: StructureInput) -> ToolResult {
    let list = context
        .clients
        .structure
        .clone()
        .list(Empty {})
        .await?
        .into_inner();
    let matching: Vec<String> = list
        .ids
        .into_iter()
        .filter(|id| id.contains(&input.filter))
        .collect();
    let total = matching.len();
    let page: Vec<String> = matching
        .into_iter()
        .take(input.limit.max(0) as usize)
        .collect();
    let data = json!({ "total": total, "ids": page });
    Ok(lines(format!("{total} matching templates"), page, data))
}

async fn save(context: Arc<Context>, input: StructureInput) -> ToolResult {
    require_fields(&[
        ("id", input.id.is_some()),
        ("from", input.from.is_some()),
        ("to", input.to.is_some()),
    ])?;
    let id = input.id.unwrap_or_default();
    let request = proto::SaveStructureRequest {
        id: id.clone(),
        dimension: dimension(input.dimension),
        from: input.from.map(Into::into),
        to: input.to.map(Into::into),
        include_entities: input.include_entities,
        ignore_block: input.ignore_block,
    };
    context.clients.structure.clone().save(request).await?;
    Ok(done(format!("Saved {id}")))
}

async fn place(context: Arc<Context>, input: StructureInput) -> ToolResult {
    require_fields(&[
        ("id", input.id.is_some()),
        ("origin", input.origin.is_some()),
    ])?;
    let rotation = match input.rotation {
        Rotation::None => StructureRotation::None,
        Rotation::Clockwise90 => StructureRotation::Clockwise90,
        Rotation::Clockwise180 => StructureRotation::Clockwise180,
        Rotation::Counterclockwise90 => StructureRotation::Counterclockwise90,
    };
    let mirror = match input.mirror {
        Mirror::None => StructureMirror::None,
        Mirror::LeftRight => StructureMirror::LeftRight,
        Mirror::FrontBack => StructureMirror::FrontBack,
    };
    let request = proto::PlaceStructureRequest {
        id: input.id.unwrap_or_default(),
        placement: Some(proto::StructurePlacement {
            dimension: dimension(input.dimension),
            origin: input.origin.map(Into::into),
            rotation: rotation as i32,
            mirror: mirror as i32,
            include_entities: input.include_entities,
            integrity: Some(input.integrity),
            no_updates: false,
        }),
    };
    Ok(message(
        &context
            .clients
            .structure
            .clone()
            .place(request)
            .await?
            .into_inner(),
    ))
}
