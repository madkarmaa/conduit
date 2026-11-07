use std::sync::Arc;

use conduit_client::{Empty, proto};
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::json;

use crate::Context;
use crate::action;
use crate::format::{lines, to_json};
use crate::schemas::default_limit;
use crate::tool::{ToolResult, ToolSpec, required};

#[derive(Deserialize, JsonSchema)]
pub struct RegistryInput {
    action: Option<String>,
    /// Required for entries and tags.
    registry: Option<String>,
    /// Case-insensitive substring of the id.
    #[serde(default)]
    filter: String,
    /// For entries: only members of this tag, e.g. "minecraft:logs".
    tag: Option<String>,
    #[serde(default)]
    #[schemars(range(min = 0))]
    offset: i32,
    /// Maximum results to return.
    #[serde(default = "default_limit")]
    #[schemars(range(min = 1, max = 1000))]
    limit: i32,
}

pub fn registry() -> ToolSpec<RegistryInput> {
    ToolSpec {
        name: "registry",
        title: "Game registries",
        description: "Looks up valid ids, including datapack content. registries lists every \
            registry, such as \"minecraft:block\", \"minecraft:item\", \"minecraft:entity_type\" and \
            \"minecraft:worldgen/biome\". entries lists the ids in one. tags lists its tags."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("registries", Read, registries),
            action!("entries", Read, entries),
            action!("tags", Read, tags),
        ],
    }
}

/// Registries and tags come back whole, so this filters and pages them like the entries.
fn page<'a, Item>(
    all: &'a [Item],
    id: impl Fn(&Item) -> &str,
    filter: &str,
    offset: i32,
    limit: i32,
) -> (usize, Vec<&'a Item>) {
    let filter = filter.to_lowercase();
    let matching: Vec<&Item> = all
        .iter()
        .filter(|item| id(item).to_lowercase().contains(&filter))
        .collect();
    let total = matching.len();
    let items = matching
        .into_iter()
        .skip(offset.max(0) as usize)
        .take(limit.max(0) as usize)
        .collect();
    (total, items)
}

async fn registries(context: Arc<Context>, input: RegistryInput) -> ToolResult {
    let list = context
        .clients
        .registry
        .clone()
        .list_registries(Empty {})
        .await?
        .into_inner();
    let (total, items) = page(
        &list.registries,
        |entry| &entry.id,
        &input.filter,
        input.offset,
        input.limit,
    );
    let rows = items
        .iter()
        .map(|entry| format!("{} ({})", entry.id, entry.entry_count))
        .collect();
    Ok(lines(
        format!("{total} matching registries"),
        rows,
        json!({ "total": total, "registries": to_json(&items) }),
    ))
}

async fn entries(context: Arc<Context>, input: RegistryInput) -> ToolResult {
    let registry = required(input.registry, "registry")?;
    let request = proto::ListRegistryEntriesRequest {
        registry,
        filter: input.filter,
        tag: input.tag.unwrap_or_default(),
        offset: input.offset,
        limit: input.limit,
    };
    let list = context
        .clients
        .registry
        .clone()
        .list_registry_entries(request)
        .await?
        .into_inner();
    let ids: Vec<String> = list.entries.into_iter().map(|entry| entry.id).collect();
    let data = json!({ "total": list.total, "ids": ids });
    Ok(lines(format!("{} matching entries", list.total), ids, data))
}

async fn tags(context: Arc<Context>, input: RegistryInput) -> ToolResult {
    let registry = required(input.registry, "registry")?;
    let list = context
        .clients
        .registry
        .clone()
        .list_tags(proto::ListTagsRequest { registry })
        .await?
        .into_inner();
    let (total, items) = page(
        &list.tags,
        |tag| &tag.id,
        &input.filter,
        input.offset,
        input.limit,
    );
    let rows = items
        .iter()
        .map(|tag| format!("#{}: {}", tag.id, tag.entries.join(" ")))
        .collect();
    Ok(lines(
        format!("{total} matching tags"),
        rows,
        json!({ "total": total, "tags": to_json(&items) }),
    ))
}
