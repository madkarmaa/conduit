use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::json;

use crate::Context;
use crate::action;
use crate::format::{done, lines};
use crate::schemas::default_limit;
use crate::tool::{ToolResult, ToolSpec, required};

#[derive(Deserialize, JsonSchema)]
pub struct RecipeInput {
    action: Option<String>,
    /// Recipe or loot table id for get and loot_table, e.g. "minecraft:crafting_table".
    id: Option<String>,
    /// e.g. "minecraft:crafting_shaped".
    #[serde(default, rename = "type")]
    kind: String,
    /// e.g. "minecraft:torch".
    #[serde(default)]
    result_item: String,
    #[serde(default)]
    #[schemars(range(min = 0))]
    offset: i32,
    /// Maximum results to return.
    #[serde(default = "default_limit")]
    #[schemars(range(min = 1, max = 1000))]
    limit: i32,
}

pub fn recipes() -> ToolSpec<RecipeInput> {
    ToolSpec {
        name: "recipes",
        title: "Recipes and loot tables",
        description: "list finds recipe ids, optionally by type or result item. get reads a recipe \
            and loot_table a loot table, such as \"minecraft:blocks/diamond_ore\", as datapack JSON."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("list", Read, list_recipes),
            action!("get", Read, get_recipe),
            action!("loot_table", Read, loot_table),
        ],
    }
}

async fn list_recipes(context: Arc<Context>, input: RecipeInput) -> ToolResult {
    let request = proto::ListRecipesRequest {
        r#type: input.kind,
        result_item: input.result_item,
        offset: input.offset,
        limit: input.limit,
    };
    let list = context
        .clients
        .registry
        .clone()
        .list_recipes(request)
        .await?
        .into_inner();
    let rows = list
        .recipes
        .iter()
        .map(|recipe| format!("{} ({})", recipe.id, recipe.r#type))
        .collect();
    let ids: Vec<&str> = list
        .recipes
        .iter()
        .map(|recipe| recipe.id.as_str())
        .collect();
    Ok(lines(
        format!("{} matching recipes", list.total),
        rows,
        json!({ "total": list.total, "ids": ids }),
    ))
}

async fn get_recipe(context: Arc<Context>, input: RecipeInput) -> ToolResult {
    let id = required(input.id, "id")?;
    let recipe = context
        .clients
        .registry
        .clone()
        .get_recipe(proto::GetRecipeRequest { id })
        .await?
        .into_inner();
    Ok(done(recipe.json))
}

async fn loot_table(context: Arc<Context>, input: RecipeInput) -> ToolResult {
    let id = required(input.id, "id")?;
    let table = context
        .clients
        .registry
        .clone()
        .get_loot_table(proto::GetLootTableRequest { id })
        .await?
        .into_inner();
    Ok(done(table.json))
}
