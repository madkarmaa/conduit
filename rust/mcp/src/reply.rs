//! What an action returns, and how that becomes an MCP tool result.

use rmcp::model::{CallToolResult, ContentBlock};
use serde_json::{Map, Value};

pub struct Reply {
    pub text: String,
    pub data: Option<Map<String, Value>>,
}

pub enum ToolError {
    Grpc {
        status: &'static str,
        details: String,
    },
    InvalidInput(String),
}

impl From<tonic::Status> for ToolError {
    fn from(status: tonic::Status) -> Self {
        Self::Grpc {
            status: code_name(status.code()),
            details: status.message().to_owned(),
        }
    }
}

pub fn invalid_input(message: impl Into<String>) -> ToolError {
    ToolError::InvalidInput(message.into())
}

pub type ToolResult = Result<Reply, ToolError>;

pub fn tool_result(reply: Reply) -> CallToolResult {
    let mut result = CallToolResult::success(vec![ContentBlock::text(reply.text)]);
    result.structured_content = reply.data.map(Value::Object);
    // Clients read a missing isError as false, so a success leaves it out.
    result.is_error = None;
    result
}

pub fn error_result(error: &ToolError) -> CallToolResult {
    let text = match error {
        ToolError::Grpc { status, details } => format!("{status}: {details}"),
        ToolError::InvalidInput(message) => format!("Invalid input: {message}"),
    };
    CallToolResult::error(vec![ContentBlock::text(text)])
}

/// The value of a field that only some actions need, which the schema has to mark optional.
pub fn required<T>(value: Option<T>, field: &str) -> Result<T, ToolError> {
    value.ok_or_else(|| invalid_input(format!("Missing {field}")))
}

/// Fields only some actions need are optional in the schema, so each action checks its own.
pub fn require_fields(fields: &[(&str, bool)]) -> Result<(), ToolError> {
    let missing: Vec<&str> = fields
        .iter()
        .filter(|(_, set)| !set)
        .map(|(name, _)| *name)
        .collect();
    if missing.is_empty() {
        Ok(())
    } else {
        Err(invalid_input(format!("Missing {}", missing.join(", "))))
    }
}

/// The gRPC status names, as the TypeScript client reports them.
fn code_name(code: tonic::Code) -> &'static str {
    use tonic::Code;
    match code {
        Code::Ok => "OK",
        Code::Cancelled => "CANCELLED",
        Code::Unknown => "UNKNOWN",
        Code::InvalidArgument => "INVALID_ARGUMENT",
        Code::DeadlineExceeded => "DEADLINE_EXCEEDED",
        Code::NotFound => "NOT_FOUND",
        Code::AlreadyExists => "ALREADY_EXISTS",
        Code::PermissionDenied => "PERMISSION_DENIED",
        Code::ResourceExhausted => "RESOURCE_EXHAUSTED",
        Code::FailedPrecondition => "FAILED_PRECONDITION",
        Code::Aborted => "ABORTED",
        Code::OutOfRange => "OUT_OF_RANGE",
        Code::Unimplemented => "UNIMPLEMENTED",
        Code::Internal => "INTERNAL",
        Code::Unavailable => "UNAVAILABLE",
        Code::DataLoss => "DATA_LOSS",
        Code::Unauthenticated => "UNAUTHENTICATED",
    }
}
