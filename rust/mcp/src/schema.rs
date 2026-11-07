//! The MCP listing of a tool: its input schema, annotations and action metadata.

use std::sync::Arc;

use rmcp::model::{JsonObject, MetaObject, Tool, ToolAnnotations};
use schemars::JsonSchema;
use schemars::generate::SchemaSettings;
use serde_json::{Map, Value, json};

use crate::tool::{Access, Action, ToolSpec};

/// Lists each offered action of a tool with its access in the tool's _meta, so a client can ask
/// for confirmation before the destructive ones.
const ACTIONS_META_KEY: &str = "top.madkarma.conduit/actions";

pub fn listing<Input: JsonSchema>(spec: &ToolSpec<Input>, allowed: &[usize]) -> Tool {
    let actions: Vec<&Action<Input>> = allowed.iter().map(|&index| &spec.actions[index]).collect();
    let schema = input_schema::<Input>(&actions, spec.default_action);

    let annotations = ToolAnnotations::new()
        .read_only(actions.iter().all(|action| action.access == Access::Read))
        .destructive(
            actions
                .iter()
                .any(|action| action.access == Access::Destructive),
        )
        .open_world(false);
    let mut meta = spec.meta.clone().unwrap_or_default();
    let accesses: Map<String, Value> = actions
        .iter()
        .map(|action| (action.name.to_owned(), json!(action.access.name())))
        .collect();
    meta.insert(ACTIONS_META_KEY.to_owned(), Value::Object(accesses));

    Tool::new(spec.name, spec.description.clone(), Arc::new(schema))
        .with_title(spec.title)
        .with_annotations(annotations)
        .with_meta(MetaObject(meta))
}

/// The input schema with its action enum limited to the allowed actions.
fn input_schema<Input: JsonSchema>(
    actions: &[&Action<Input>],
    default_action: Option<&str>,
) -> JsonObject {
    let generator = SchemaSettings::draft2020_12()
        .with(|settings| {
            settings.inline_subschemas = true;
            settings.meta_schema = None;
        })
        .into_generator();
    let mut schema = match generator.into_root_schema_for::<Input>().to_value() {
        Value::Object(schema) => schema,
        _ => Map::new(),
    };
    schema.remove("title");
    strip_nullable(&mut schema);

    if let Some(Value::Object(properties)) = schema.get_mut("properties")
        && let Some(Value::Object(action)) = properties.get_mut("action")
    {
        let names: Vec<Value> = actions.iter().map(|action| json!(action.name)).collect();
        action.clear();
        action.insert("type".into(), json!("string"));
        action.insert("enum".into(), Value::Array(names));
        match default_action {
            Some(name) if actions.iter().any(|action| action.name == name) => {
                action.insert("default".into(), json!(name));
            }
            _ => require(&mut schema, "action"),
        }
    }
    schema
}

fn require(schema: &mut JsonObject, field: &str) {
    let required = schema.entry("required").or_insert_with(|| json!([]));
    if let Value::Array(names) = required
        && !names.iter().any(|name| name == field)
    {
        names.push(json!(field));
    }
}

/// Optional fields are left out of required, so their schemas need no null type. Formats such as
/// int32 only repeat the type, so they go too.
fn strip_nullable(schema: &mut Map<String, Value>) {
    if schema.get("format").is_some_and(Value::is_string) {
        schema.remove("format");
    }
    if let Some(Value::Array(allowed)) = schema.get_mut("enum") {
        allowed.retain(|value| !value.is_null());
    }
    if let Some(Value::Array(types)) = schema.get_mut("type") {
        types.retain(|kind| kind != "null");
        if types.len() == 1 {
            let only = types.remove(0);
            schema.insert("type".into(), only);
        }
    }
    for value in schema.values_mut() {
        match value {
            Value::Object(child) => strip_nullable(child),
            Value::Array(items) => items
                .iter_mut()
                .filter_map(Value::as_object_mut)
                .for_each(strip_nullable),
            _ => {}
        }
    }
}
