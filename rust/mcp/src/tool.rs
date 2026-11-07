//! Tool definitions and how a call reaches the action it names.

use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;

use rmcp::model::{CallToolResult, Tool};
use schemars::JsonSchema;
use serde::de::DeserializeOwned;
use serde_json::{Map, Value};

use crate::Context;
use crate::policy::{ToolPolicy, check_players, offers, redact};
// The tools import their reply types from here, next to the tool types.
pub use crate::reply::{Reply, ToolError, ToolResult, invalid_input, require_fields, required};
use crate::reply::{error_result, tool_result};
use crate::schema::listing;
use crate::validate;

/// Destructive actions are writes that are hard to undo, such as stopping the server or kicking a
/// player.
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Access {
    Read,
    Write,
    Destructive,
}

impl Access {
    pub fn name(self) -> &'static str {
        match self {
            Self::Read => "read",
            Self::Write => "write",
            Self::Destructive => "destructive",
        }
    }
}

pub type ActionFuture = Pin<Box<dyn Future<Output = ToolResult> + Send>>;

pub struct Action<Input> {
    pub name: &'static str,
    pub access: Access,
    pub run: fn(Arc<Context>, Input) -> ActionFuture,
}

/// Tools with several actions have an `action` field in their input with the same names.
pub struct ToolSpec<Input> {
    pub name: &'static str,
    pub title: &'static str,
    pub description: String,
    /// The action to run when the input names none.
    pub default_action: Option<&'static str>,
    /// Returns data about players without naming them in a player argument, so a server limited to
    /// one player cannot offer it.
    pub sees_other_players: bool,
    pub meta: Option<Map<String, Value>>,
    pub actions: Vec<Action<Input>>,
}

/// A tool as the server offers it under its policy.
pub trait OfferedTool: Send + Sync {
    fn name(&self) -> &'static str;
    fn listing(&self) -> &Tool;
    fn call(
        &self,
        context: Arc<Context>,
        arguments: Value,
    ) -> Pin<Box<dyn Future<Output = CallToolResult> + Send>>;
}

struct Offered<Input> {
    spec: ToolSpec<Input>,
    allowed: Vec<usize>,
    listing: Tool,
    policy: Arc<ToolPolicy>,
}

/// Returns the tool with only the actions the policy allows, or none if it allows none.
pub fn offer<Input>(spec: ToolSpec<Input>, policy: &Arc<ToolPolicy>) -> Option<Box<dyn OfferedTool>>
where
    Input: DeserializeOwned + JsonSchema + Send + 'static,
{
    if !offers(policy, spec.name, spec.sees_other_players) {
        return None;
    }
    let allowed: Vec<usize> = (0..spec.actions.len())
        .filter(|&index| !policy.read_only || spec.actions[index].access == Access::Read)
        .collect();
    if allowed.is_empty() {
        return None;
    }

    let listing = listing(&spec, &allowed);
    Some(Box::new(Offered {
        spec,
        allowed,
        listing,
        policy: Arc::clone(policy),
    }))
}

impl<Input> OfferedTool for Offered<Input>
where
    Input: DeserializeOwned + JsonSchema + Send + 'static,
{
    fn name(&self) -> &'static str {
        self.spec.name
    }

    fn listing(&self) -> &Tool {
        &self.listing
    }

    fn call(
        &self,
        context: Arc<Context>,
        arguments: Value,
    ) -> Pin<Box<dyn Future<Output = CallToolResult> + Send>> {
        let prepared = validate::check(&self.listing.input_schema, &arguments, "")
            .map_err(invalid_input)
            .and_then(|()| check_players(&arguments, &self.policy.player))
            .and_then(|()| self.select(&arguments))
            .and_then(|action| {
                let input = serde_json::from_value::<Input>(arguments)
                    .map_err(|error| invalid_input(error.to_string()))?;
                Ok((action.run)(context, input))
            });
        let limited = !self.policy.player.is_empty();

        Box::pin(async move {
            let outcome = match prepared {
                Ok(running) => running.await,
                Err(error) => Err(error),
            };
            match outcome {
                Ok(reply) => tool_result(if limited { redact(reply) } else { reply }),
                Err(error) => error_result(&error),
            }
        })
    }
}

impl<Input> Offered<Input> {
    fn select(&self, arguments: &Value) -> Result<&Action<Input>, ToolError> {
        let actions: Vec<&Action<Input>> = self
            .allowed
            .iter()
            .map(|&index| &self.spec.actions[index])
            .collect();
        if let [only] = actions.as_slice() {
            return Ok(only);
        }
        let requested = arguments
            .get("action")
            .and_then(Value::as_str)
            .or(self.spec.default_action);
        actions
            .into_iter()
            .find(|action| Some(action.name) == requested)
            .ok_or_else(|| {
                invalid_input(format!(
                    "Unknown action {}",
                    requested.unwrap_or("undefined")
                ))
            })
    }
}

/// Boxes an action's async function, so a tool can list it next to the others.
#[macro_export]
macro_rules! action {
    ($name:literal, $access:ident, $run:path) => {
        $crate::tool::Action {
            name: $name,
            access: $crate::tool::Access::$access,
            run: |context, input| Box::pin($run(context, input)),
        }
    };
}
