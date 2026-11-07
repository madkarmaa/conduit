use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use super::entity_status;
use crate::Context;
use crate::action;
use crate::format::done;
use crate::schemas::{to_text, yes};
use crate::tool::{ToolResult, ToolSpec, required};

#[derive(Deserialize, JsonSchema)]
pub struct EditInput {
    pub(super) action: Option<String>,
    /// Entity UUID.
    pub(super) uuid: String,
    /// New custom name. Leave it out to clear it.
    pub(super) name: Option<String>,
    #[serde(default)]
    pub(super) name_visible: bool,
    pub(super) add_tags: Option<Vec<String>>,
    pub(super) remove_tags: Option<Vec<String>>,
    /// Health or damage amount.
    #[schemars(range(min = 0))]
    pub(super) amount: Option<f32>,
    #[serde(default = "generic_damage")]
    pub(super) damage_type: String,
    /// UUID of the attacking entity.
    pub(super) attacker: Option<String>,
    /// Required for nbt.
    pub(super) snbt: Option<String>,
    /// Effect id, e.g. "minecraft:speed"; remove_effect without it clears all.
    pub(super) effect: Option<String>,
    /// -1 means infinite.
    #[serde(default = "default_duration")]
    #[schemars(range(min = -1))]
    pub(super) duration_ticks: i32,
    /// 0 is level I.
    #[serde(default)]
    #[schemars(range(min = 0, max = 255))]
    pub(super) amplifier: i32,
    #[serde(default = "yes")]
    pub(super) show_particles: bool,
    /// Required for set_attribute.
    pub(super) attribute: Option<String>,
    /// Required for set_attribute.
    pub(super) base: Option<f64>,
}

fn generic_damage() -> String {
    "minecraft:generic".into()
}

fn default_duration() -> i32 {
    600
}

pub fn edit() -> ToolSpec<EditInput> {
    ToolSpec {
        name: "edit_entity",
        title: "Edit entity",
        description: "name sets or clears the custom name. tags adds and removes scoreboard tags. \
            health sets the health. damage hurts the entity like an attack. nbt merges SNBT into \
            its data. add_effect and remove_effect change status effects. set_attribute sets an \
            attribute base value such as \"minecraft:max_health\"."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("name", Write, set_name),
            action!("tags", Write, set_tags),
            action!("health", Write, entity_status::set_health),
            action!("damage", Write, entity_status::damage),
            action!("nbt", Write, merge_nbt),
            action!("add_effect", Write, entity_status::add_effect),
            action!("remove_effect", Write, entity_status::remove_effect),
            action!("set_attribute", Write, entity_status::set_attribute),
        ],
    }
}

async fn set_name(context: Arc<Context>, input: EditInput) -> ToolResult {
    let named = input.name.as_ref().is_some_and(|name| !name.is_empty());
    let request = proto::SetCustomNameRequest {
        uuid: input.uuid,
        name: input.name.map(to_text),
        visible: input.name_visible,
    };
    context
        .clients
        .entity
        .clone()
        .set_custom_name(request)
        .await?;
    Ok(done(if named { "Name set" } else { "Name cleared" }))
}

async fn set_tags(context: Arc<Context>, input: EditInput) -> ToolResult {
    let request = proto::SetTagsRequest {
        uuid: input.uuid,
        add: input.add_tags.unwrap_or_default(),
        remove: input.remove_tags.unwrap_or_default(),
    };
    context.clients.entity.clone().set_tags(request).await?;
    Ok(done("Tags updated"))
}

async fn merge_nbt(context: Arc<Context>, input: EditInput) -> ToolResult {
    let snbt = required(input.snbt, "snbt")?;
    context
        .clients
        .entity
        .clone()
        .merge_nbt(proto::MergeNbtRequest {
            uuid: input.uuid,
            snbt,
        })
        .await?;
    Ok(done("Entity data merged"))
}
