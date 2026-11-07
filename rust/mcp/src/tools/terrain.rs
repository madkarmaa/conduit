use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::Context;
use crate::action;
use crate::format::{done, message};
use crate::schemas::dimension;
use crate::tool::{ToolResult, ToolSpec, require_fields};

#[derive(Deserialize, Serialize, JsonSchema)]
#[allow(non_camel_case_types, clippy::upper_case_acronyms)]
enum Heightmap {
    WORLD_SURFACE,
    MOTION_BLOCKING,
    MOTION_BLOCKING_NO_LEAVES,
    OCEAN_FLOOR,
}

fn motion_blocking() -> Heightmap {
    Heightmap::MOTION_BLOCKING
}

#[derive(Deserialize, JsonSchema)]
pub struct TerrainInput {
    action: Option<String>,
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    /// Block x of the column. Required for height and chunks.
    x: Option<i32>,
    /// Block z of the column. Required for height and chunks.
    z: Option<i32>,
    #[serde(default = "motion_blocking")]
    heightmap: Heightmap,
}

pub fn terrain() -> ToolSpec<TerrainInput> {
    ToolSpec {
        name: "terrain",
        title: "Terrain",
        description: "height: y of the first free block above the surface of a column. chunk: \
            whether the chunk containing a column is loaded or force-loaded, with its entity counts."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("height", Read, height), action!("chunk", Read, chunk)],
    }
}

async fn height(context: Arc<Context>, input: TerrainInput) -> ToolResult {
    require_fields(&[("x", input.x.is_some()), ("z", input.z.is_some())])?;
    let heightmap = match input.heightmap {
        Heightmap::WORLD_SURFACE => "WORLD_SURFACE",
        Heightmap::MOTION_BLOCKING => "MOTION_BLOCKING",
        Heightmap::MOTION_BLOCKING_NO_LEAVES => "MOTION_BLOCKING_NO_LEAVES",
        Heightmap::OCEAN_FLOOR => "OCEAN_FLOOR",
    };
    let request = proto::GetHeightRequest {
        dimension: dimension(input.dimension),
        x: input.x.unwrap_or_default(),
        z: input.z.unwrap_or_default(),
        heightmap: heightmap.into(),
    };
    let response = context
        .clients
        .world
        .clone()
        .get_height(request)
        .await?
        .into_inner();
    Ok(done(format!("y={}", response.y)))
}

async fn chunk(context: Arc<Context>, input: TerrainInput) -> ToolResult {
    require_fields(&[("x", input.x.is_some()), ("z", input.z.is_some())])?;
    let request = chunk_of(
        input.dimension,
        input.x.unwrap_or_default(),
        input.z.unwrap_or_default(),
    );
    Ok(message(
        &context
            .clients
            .world
            .clone()
            .get_chunk(request)
            .await?
            .into_inner(),
    ))
}

pub(super) fn chunk_of(dimension_id: Option<String>, x: i32, z: i32) -> proto::ChunkRequest {
    proto::ChunkRequest {
        dimension: dimension(dimension_id),
        chunk_x: x.div_euclid(16),
        chunk_z: z.div_euclid(16),
    }
}
