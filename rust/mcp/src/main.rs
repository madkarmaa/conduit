//! MCP server over stdio that lets AI agents control Minecraft through the Conduit mod.

mod event_types;
mod events;
mod format;
mod policy;
mod region;
mod reply;
mod schema;
mod schemas;
mod tool;
mod tools;
mod validate;

use std::collections::HashMap;
use std::sync::Arc;

use conduit_client::{ConduitClients, connect, connection_from_env};
use rmcp::model::{
    CallToolRequestParams, CallToolResponse, Implementation, ListToolsResult,
    PaginatedRequestParams, ServerCapabilities, ServerConfig,
};
use rmcp::service::RequestContext;
use rmcp::{ErrorData, RoleServer, ServerHandler, ServiceExt};
use serde_json::Value;

use crate::events::EventFeed;
use crate::policy::ToolPolicy;
use crate::tool::OfferedTool;

const INSTRUCTIONS: &str = "Controls a running Minecraft server through the Conduit mod. \
    Positions are block coordinates, and y grows upwards. Dimensions default to the overworld. \
    Use the registry tool to look up valid block, item and entity ids instead of guessing them. \
    Prefer the dedicated tools. run_command covers anything they do not.";

/// What every action can use.
pub struct Context {
    pub clients: ConduitClients,
    pub feed: EventFeed,
}

struct Server {
    context: Arc<Context>,
    tools: Arc<HashMap<&'static str, Box<dyn OfferedTool>>>,
    order: Arc<Vec<&'static str>>,
    instructions: String,
}

impl ServerHandler for Server {
    fn get_info(&self) -> ServerConfig {
        ServerConfig::new(ServerCapabilities::builder().enable_tools().build())
            .with_server_info(Implementation::new("conduit", env!("CARGO_PKG_VERSION")))
            .with_instructions(self.instructions.clone())
    }

    async fn list_tools(
        &self,
        _request: Option<PaginatedRequestParams>,
        _context: RequestContext<RoleServer>,
    ) -> Result<ListToolsResult, ErrorData> {
        let tools = self
            .order
            .iter()
            .map(|name| self.tools[name].listing().clone())
            .collect();
        Ok(ListToolsResult::with_all_items(tools))
    }

    async fn call_tool(
        &self,
        request: CallToolRequestParams,
        _context: RequestContext<RoleServer>,
    ) -> Result<CallToolResponse, ErrorData> {
        let Some(tool) = self.tools.get(request.name.as_ref()) else {
            return Err(ErrorData::invalid_params(
                format!("Tool {} not found", request.name),
                None,
            ));
        };
        let arguments = Value::Object(request.arguments.unwrap_or_default());
        Ok(tool.call(Arc::clone(&self.context), arguments).await.into())
    }
}

#[tokio::main(flavor = "current_thread")]
async fn main() {
    let clients = match connection_from_env()
        .and_then(|options| connect(&options).map_err(|error| error.to_string()))
    {
        Ok(clients) => clients,
        Err(message) => {
            eprintln!("{message}");
            std::process::exit(1);
        }
    };
    let policy = Arc::new(ToolPolicy::from_env());
    let feed = EventFeed::new(clients.events.clone());
    let context = Arc::new(Context { clients, feed });

    let offered = tools::all(&policy);
    let order = offered.iter().map(|tool| tool.name()).collect();
    let tools = offered
        .into_iter()
        .map(|tool| (tool.name(), tool))
        .collect();
    let server = Server {
        context,
        tools: Arc::new(tools),
        order: Arc::new(order),
        instructions: instructions(&policy),
    };

    match server.serve(rmcp::transport::stdio()).await {
        Ok(running) => {
            let _ = running.waiting().await;
        }
        Err(error) => {
            eprintln!("Cannot start the MCP server: {error}");
            std::process::exit(1);
        }
    }
}

fn instructions(policy: &ToolPolicy) -> String {
    match policy.player.first() {
        None => INSTRUCTIONS.to_owned(),
        Some(player) => {
            format!(
                "{INSTRUCTIONS} The tools only accept and return data about the player {player}."
            )
        }
    }
}
