use std::sync::Arc;

use conduit_client::{Code, proto};
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Value, json};

use crate::Context;
use crate::action;
use crate::format::{done, json, to_json};
use crate::schemas::{BlockPos, dimension, yes};
use crate::tool::{ToolResult, ToolSpec, require_fields};

#[derive(Deserialize, JsonSchema)]
pub struct GetBlockInput {
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    pos: BlockPos,
}

#[derive(Deserialize, JsonSchema)]
pub struct BlockChange {
    pos: BlockPos,
    /// Block state in command syntax, e.g. "minecraft:oak_stairs[facing=east]".
    state: String,
}

#[derive(Deserialize, JsonSchema)]
pub struct EditBlocksInput {
    action: Option<String>,
    /// Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.
    dimension: Option<String>,
    /// Required for set.
    #[schemars(length(min = 1))]
    changes: Option<Vec<BlockChange>>,
    /// Fill corner.
    from: Option<BlockPos>,
    /// Opposite fill corner.
    to: Option<BlockPos>,
    /// Required for fill.
    state: Option<String>,
    /// fill only replaces blocks matching this block id or state.
    replace: Option<String>,
    /// Required for break and merge_nbt.
    pos: Option<BlockPos>,
    /// break drops the block's items.
    #[serde(default = "yes")]
    drop_items: bool,
    /// Required for merge_nbt.
    snbt: Option<String>,
    /// Skip neighbor updates and physics, like structure placement.
    #[serde(default)]
    no_updates: bool,
}

pub fn get_block() -> ToolSpec<GetBlockInput> {
    ToolSpec {
        name: "get_block",
        title: "Get block",
        description: "Block state, biome and block entity SNBT (chest, sign, ...) at a position."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("get", Read, read_block)],
    }
}

async fn read_block(context: Arc<Context>, input: GetBlockInput) -> ToolResult {
    let mut world = context.clients.world.clone();
    let request = proto::GetBlockRequest {
        dimension: dimension(input.dimension),
        pos: Some(input.pos.into()),
    };
    let block = world.get_block(request.clone()).await?.into_inner();
    let biome = world.get_biome(request.clone()).await?.into_inner();
    // Most blocks have no block entity, which the server reports as NOT_FOUND.
    let block_entity = match world.get_block_entity(request).await {
        Ok(nbt) => Some(nbt.into_inner().snbt),
        Err(status) if status.code() == Code::NotFound => None,
        Err(status) => return Err(status.into()),
    };

    let mut value = to_json(&block);
    if let Value::Object(fields) = &mut value {
        fields.insert("biome".into(), json!(biome.id));
        fields.insert("blockEntity".into(), json!(block_entity));
    }
    Ok(json(value))
}

pub fn edit_blocks() -> ToolSpec<EditBlocksInput> {
    ToolSpec {
        name: "edit_blocks",
        title: "Edit blocks",
        description: "set places block states at positions in one tick. fill fills a cuboid, \
            corners inclusive. break breaks a block like a player. merge_nbt merges SNBT into a \
            block entity."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("set", Write, set),
            action!("fill", Destructive, fill),
            action!("break", Write, break_block),
            action!("merge_nbt", Write, merge_nbt),
        ],
    }
}

async fn set(context: Arc<Context>, input: EditBlocksInput) -> ToolResult {
    require_fields(&[("changes", input.changes.is_some())])?;
    let changes = input
        .changes
        .unwrap_or_default()
        .into_iter()
        .map(|change| proto::BlockChange {
            pos: Some(change.pos.into()),
            state: change.state,
        })
        .collect();
    let request = proto::SetBlocksRequest {
        dimension: dimension(input.dimension),
        changes,
        no_updates: input.no_updates,
    };
    let response = context
        .clients
        .world
        .clone()
        .set_blocks(request)
        .await?
        .into_inner();
    Ok(done(format!("Changed {} blocks", response.changed)))
}

async fn fill(context: Arc<Context>, input: EditBlocksInput) -> ToolResult {
    require_fields(&[
        ("from", input.from.is_some()),
        ("to", input.to.is_some()),
        ("state", input.state.is_some()),
    ])?;
    let request = proto::FillRequest {
        dimension: dimension(input.dimension),
        from: input.from.map(Into::into),
        to: input.to.map(Into::into),
        state: input.state.unwrap_or_default(),
        no_updates: input.no_updates,
        replace: input.replace,
    };
    let response = context
        .clients
        .world
        .clone()
        .fill(request)
        .await?
        .into_inner();
    Ok(done(format!("Changed {} blocks", response.changed)))
}

async fn break_block(context: Arc<Context>, input: EditBlocksInput) -> ToolResult {
    require_fields(&[("pos", input.pos.is_some())])?;
    let request = proto::BreakBlockRequest {
        dimension: dimension(input.dimension),
        pos: input.pos.map(Into::into),
        drop_items: input.drop_items,
    };
    context.clients.world.clone().break_block(request).await?;
    Ok(done("Block broken"))
}

async fn merge_nbt(context: Arc<Context>, input: EditBlocksInput) -> ToolResult {
    require_fields(&[("pos", input.pos.is_some()), ("snbt", input.snbt.is_some())])?;
    let request = proto::SetBlockEntityRequest {
        dimension: dimension(input.dimension),
        pos: input.pos.map(Into::into),
        snbt: input.snbt.unwrap_or_default(),
    };
    context
        .clients
        .world
        .clone()
        .set_block_entity(request)
        .await?;
    Ok(done("Block entity updated"))
}
