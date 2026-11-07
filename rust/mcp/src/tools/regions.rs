use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::region::{draw_region, palette_fits, summarize_region};
use crate::schemas::{BlockPos, dimension};
use crate::tool::{ToolResult, ToolSpec, invalid_input};

/// Drawn regions print one character per block, so they stay small enough to read.
const MAX_DRAWN_VOLUME: i64 = 8192;

#[derive(Deserialize, JsonSchema)]
pub struct RegionInput {
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    from: BlockPos,
    to: BlockPos,
    #[serde(default)]
    detail: Detail,
    /// Do not load chunks. Unloaded positions read as void air.
    #[serde(default)]
    loaded_only: bool,
}

#[derive(Default, Deserialize, serde::Serialize, JsonSchema, PartialEq)]
#[serde(rename_all = "lowercase")]
enum Detail {
    #[default]
    Summary,
    Draw,
}

pub fn get_region() -> ToolSpec<RegionInput> {
    ToolSpec {
        name: "get_region",
        title: "Read region",
        description: format!(
            "Reads a cuboid region, corners inclusive. 'summary' counts each block state. 'draw' \
             prints every y layer as a character grid, up to {MAX_DRAWN_VOLUME} blocks."
        ),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("read", Read, read_region)],
    }
}

async fn read_region(context: Arc<Context>, input: RegionInput) -> ToolResult {
    let drawing = input.detail == Detail::Draw;
    if drawing && volume(input.from, input.to) > MAX_DRAWN_VOLUME {
        return Err(invalid_input(format!(
            "draw supports up to {MAX_DRAWN_VOLUME} blocks"
        )));
    }
    let request = proto::GetBlocksRequest {
        dimension: dimension(input.dimension),
        from: Some(input.from.into()),
        to: Some(input.to.into()),
        loaded_only: input.loaded_only,
    };
    let region = context
        .clients
        .world
        .clone()
        .get_blocks(request)
        .await?
        .into_inner();
    if !drawing {
        return Ok(summarize_region(&region));
    }
    if palette_fits(&region) {
        return Ok(draw_region(&region));
    }
    Err(invalid_input("Too many block states to draw"))
}

fn volume(from: BlockPos, to: BlockPos) -> i64 {
    let side = |first: i32, second: i32| i64::from((first - second).abs()) + 1;
    side(from.x, to.x) * side(from.y, to.y) * side(from.z, to.z)
}
