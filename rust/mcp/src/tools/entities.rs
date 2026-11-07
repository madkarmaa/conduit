use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Value, json};

use crate::Context;
use crate::action;
use crate::format::{compact, describe_entity, done, lines, message, to_json};
use crate::schemas::{Vec3, default_limit, dimension, entity_ref};
use crate::tool::{ToolResult, ToolSpec};

#[derive(Deserialize, JsonSchema)]
pub struct Area {
    min: Vec3,
    max: Vec3,
}

#[derive(Deserialize, JsonSchema)]
pub struct ListInput {
    /// Empty means every dimension.
    dimension: Option<String>,
    /// e.g. ["minecraft:zombie"].
    types: Option<Vec<String>>,
    /// Requires dimension and radius.
    center: Option<Vec3>,
    #[schemars(range(min = 0))]
    radius: Option<f64>,
    #[serde(rename = "box")]
    area: Option<Area>,
    /// Required scoreboard tag.
    tag: Option<String>,
    /// Selector such as "@e[type=cow,distance=..10]"; overrides other filters.
    selector: Option<String>,
    /// Maximum results to return.
    #[serde(default = "default_limit")]
    #[schemars(range(min = 1, max = 1000))]
    limit: i32,
}

#[derive(Deserialize, JsonSchema)]
pub struct EntityInput {
    action: Option<String>,
    /// Entity UUID.
    uuid: String,
}

pub fn list() -> ToolSpec<ListInput> {
    ToolSpec {
        name: "list_entities",
        title: "List entities",
        description:
            "Finds entities by type, area, tag or a vanilla selector. Each line lists one \
            entity with its uuid, type, name, dimension, position and health."
                .into(),
        default_action: None,
        sees_other_players: true,
        meta: None,
        actions: vec![action!("list", Read, list_entities)],
    }
}

async fn list_entities(context: Arc<Context>, input: ListInput) -> ToolResult {
    let request = proto::ListEntitiesRequest {
        dimension: dimension(input.dimension),
        types: input.types.unwrap_or_default(),
        center: input.center.map(Into::into),
        radius: input.radius,
        r#box: input.area.map(|area| proto::Box {
            min: Some(area.min.into()),
            max: Some(area.max.into()),
        }),
        selector: input.selector,
        tag: input.tag,
        limit: input.limit,
    };
    let list = context
        .clients
        .entity
        .clone()
        .list_entities(request)
        .await?
        .into_inner();
    let entities: Vec<Value> = list
        .entities
        .iter()
        .map(|entity| compact(to_json(entity)))
        .collect();
    let rows = entities.iter().map(describe_entity).collect();
    Ok(lines(
        format!("{} entities", entities.len()),
        rows,
        json!({ "entities": entities }),
    ))
}

pub fn get() -> ToolSpec<EntityInput> {
    ToolSpec {
        name: "get_entity",
        title: "Get entity",
        description:
            "info: full state of one entity. nbt: its complete data as SNBT. effects: its \
            status effects. attributes: its attributes with base and current values."
                .into(),
        default_action: Some("info"),
        sees_other_players: true,
        meta: None,
        actions: vec![
            action!("info", Read, info),
            action!("nbt", Read, nbt),
            action!("effects", Read, effects),
            action!("attributes", Read, attributes),
        ],
    }
}

async fn info(context: Arc<Context>, input: EntityInput) -> ToolResult {
    Ok(message(
        &context
            .clients
            .entity
            .clone()
            .get_entity(entity_ref(input.uuid))
            .await?
            .into_inner(),
    ))
}

async fn nbt(context: Arc<Context>, input: EntityInput) -> ToolResult {
    Ok(done(
        context
            .clients
            .entity
            .clone()
            .get_nbt(entity_ref(input.uuid))
            .await?
            .into_inner()
            .snbt,
    ))
}

async fn effects(context: Arc<Context>, input: EntityInput) -> ToolResult {
    Ok(message(
        &context
            .clients
            .entity
            .clone()
            .get_effects(entity_ref(input.uuid))
            .await?
            .into_inner(),
    ))
}

async fn attributes(context: Arc<Context>, input: EntityInput) -> ToolResult {
    Ok(message(
        &context
            .clients
            .entity
            .clone()
            .get_attributes(entity_ref(input.uuid))
            .await?
            .into_inner(),
    ))
}
