use std::sync::Arc;

use conduit_client::proto::BatchRequest;
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Map, Value, json};

use crate::Context;
use crate::action;
use crate::format::{compact, lines, to_json};
use crate::tool::{ToolResult, ToolSpec, invalid_input};

/// The batch is destructive when it holds any of these operations, the ones that match the
/// destructive actions of the other tools.
const DESTRUCTIVE_OPERATIONS: &[&str] = &[
    "executeCommand",
    "fill",
    "explode",
    "removeEntity",
    "clearInventory",
    "setOperator",
    "kick",
    "setGameRule",
    "setWorldBorder",
];

const DESTRUCTIVE_OPERATIONS_META_KEY: &str = "top.madkarma.conduit/destructiveOperations";

#[derive(Deserialize, JsonSchema)]
pub struct BatchInput {
    #[schemars(length(min = 1))]
    operations: Vec<Operation>,
    /// Skip the rest after a failure.
    #[serde(default)]
    stop_on_error: bool,
}

/// One operation, e.g. {"setBlock": {"pos": {"x": 0, "y": 64, "z": 0}, "state": "minecraft:stone"}}.
#[derive(Deserialize, JsonSchema)]
#[serde(transparent)]
struct Operation(Map<String, Value>);

pub fn batch() -> ToolSpec<BatchInput> {
    let mut meta = Map::new();
    meta.insert(
        DESTRUCTIVE_OPERATIONS_META_KEY.into(),
        json!(DESTRUCTIVE_OPERATIONS),
    );
    ToolSpec {
        name: "batch",
        title: "Batch",
        description: "Runs many operations in order within one server tick. Each operation is an \
            object with a single key naming the gRPC method in camelCase and its request with \
            camelCase fields. Methods: getBlock, getBlocks, setBlock, setBlocks, fill, breakBlock, \
            getBlockEntity, setBlockEntity, getBiome, getHeight, getChunk, setChunkForced, getTime, \
            setTime, getWeather, setWeather, getGameRules, setGameRule, getSpawn, setSpawn, \
            explode, getWorldBorder, setWorldBorder, listEntities, getEntity, spawnEntity, \
            removeEntity, teleport, setVelocity, setRotation, getNbt, mergeNbt, setCustomName, \
            setTags, damage, setHealth, getEffects, addEffect, removeEffect, getAttributes, \
            setAttributeBase, mount, dismount, listPlayers, getPlayer, getInventory, \
            setInventorySlot, giveItem, clearInventory, setSelectedSlot, getEnderChest, \
            setGameMode, setFood, setExperience, sendMessage, sendTitle, playSound, kick, \
            setOperator, executeCommand, broadcast. Text fields take {\"plain\": \"...\"}."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: Some(meta),
        actions: vec![action!("run", Destructive, run)],
    }
}

async fn run(context: Arc<Context>, input: BatchInput) -> ToolResult {
    // Operations use the protobuf JSON form, which the generated messages read directly. The
    // server validates each operation and reports unknown ones per result.
    let operations: Vec<Value> = input
        .operations
        .into_iter()
        .map(|operation| Value::Object(operation.0))
        .collect();
    let request: BatchRequest = serde_json::from_value(json!({
        "operations": operations,
        "stopOnError": input.stop_on_error,
    }))
    .map_err(|error| invalid_input(error.to_string()))?;

    let response = context
        .clients
        .batch
        .clone()
        .execute(request)
        .await?
        .into_inner();
    let results: Vec<Value> = response
        .results
        .iter()
        .map(|result| compact(to_json(result)))
        .collect();
    let rows = results
        .iter()
        .enumerate()
        .map(|(index, result)| {
            let mut row = Map::new();
            row.insert("index".into(), json!(index));
            if let Value::Object(fields) = result {
                row.extend(fields.clone());
            }
            Value::Object(row).to_string()
        })
        .collect();
    Ok(lines(
        format!("{} results", results.len()),
        rows,
        json!({ "results": results }),
    ))
}
