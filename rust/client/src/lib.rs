//! Client for the gRPC API of the Conduit Minecraft mod.

use std::fmt;

use tonic::metadata::{Ascii, MetadataValue};
use tonic::service::Interceptor;
use tonic::service::interceptor::InterceptedService;
use tonic::transport::{Channel, Endpoint};

pub mod proto {
    #![allow(clippy::all, missing_docs)]
    tonic::include_proto!("conduit.v1");
    include!(concat!(env!("OUT_DIR"), "/conduit.v1.serde.rs"));
}

pub use pbjson_types::Empty;
pub use tonic::{Code, Status};

use proto::batch_service_client::BatchServiceClient;
use proto::entity_service_client::EntityServiceClient;
use proto::event_service_client::EventServiceClient;
use proto::player_service_client::PlayerServiceClient;
use proto::registry_service_client::RegistryServiceClient;
use proto::server_service_client::ServerServiceClient;
use proto::structure_service_client::StructureServiceClient;
use proto::world_service_client::WorldServiceClient;

pub const DEFAULT_HOST: &str = "localhost";
pub const DEFAULT_PORT: u16 = 25585;

#[derive(Clone, Debug, Default)]
pub struct ConnectionOptions {
    pub host: Option<String>,
    pub port: Option<u16>,
    pub token: Option<String>,
}

/// The channel with the token on every call.
pub type AuthorizedChannel = InterceptedService<Channel, Authorization>;

/// A client for every service, all sharing one channel.
#[derive(Clone)]
pub struct ConduitClients {
    pub batch: BatchServiceClient<AuthorizedChannel>,
    pub entity: EntityServiceClient<AuthorizedChannel>,
    pub events: EventServiceClient<AuthorizedChannel>,
    pub player: PlayerServiceClient<AuthorizedChannel>,
    pub registry: RegistryServiceClient<AuthorizedChannel>,
    pub server: ServerServiceClient<AuthorizedChannel>,
    pub structure: StructureServiceClient<AuthorizedChannel>,
    pub world: WorldServiceClient<AuthorizedChannel>,
}

#[derive(Debug)]
pub enum ConnectError {
    InvalidAddress(String),
    InvalidToken,
}

impl fmt::Display for ConnectError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidAddress(address) => write!(formatter, "Invalid server address {address}"),
            Self::InvalidToken => write!(formatter, "The token is not a valid header value"),
        }
    }
}

impl std::error::Error for ConnectError {}

/// Connects lazily, so the first call opens the connection and a server that starts later still
/// works.
pub fn connect(options: &ConnectionOptions) -> Result<ConduitClients, ConnectError> {
    let host = options.host.as_deref().unwrap_or(DEFAULT_HOST);
    let port = options.port.unwrap_or(DEFAULT_PORT);
    // URIs need IPv6 hosts in brackets, to tell their colons from the port's.
    let address = if host.contains(':') && !host.starts_with('[') {
        format!("http://[{host}]:{port}")
    } else {
        format!("http://{host}:{port}")
    };

    let channel = Endpoint::from_shared(address.clone())
        .map_err(|_| ConnectError::InvalidAddress(address))?
        .connect_lazy();
    let authorization = Authorization::new(options.token.as_deref())?;
    let authorized = InterceptedService::new(channel, authorization);

    Ok(ConduitClients {
        batch: BatchServiceClient::new(authorized.clone()),
        entity: EntityServiceClient::new(authorized.clone()),
        events: EventServiceClient::new(authorized.clone()),
        player: PlayerServiceClient::new(authorized.clone()),
        registry: RegistryServiceClient::new(authorized.clone()),
        server: ServerServiceClient::new(authorized.clone()),
        structure: StructureServiceClient::new(authorized.clone()),
        world: WorldServiceClient::new(authorized),
    })
}

/// Reads the variables that the mod reads for its own host and port.
pub fn connection_from_env() -> Result<ConnectionOptions, String> {
    let read = |name: &str| std::env::var(name).ok().filter(|value| !value.is_empty());
    let port = match read("CONDUIT_PORT") {
        None => None,
        Some(value) => Some(
            value
                .parse::<u16>()
                .ok()
                .filter(|port| *port > 0)
                .ok_or_else(|| format!("CONDUIT_PORT must be a port number, not {value}"))?,
        ),
    };

    Ok(ConnectionOptions {
        host: read("CONDUIT_HOST"),
        port,
        token: read("CONDUIT_TOKEN"),
    })
}

/// Adds the bearer token to every call. The mod only serves plaintext.
#[derive(Clone)]
pub struct Authorization {
    header: Option<MetadataValue<Ascii>>,
}

impl Authorization {
    fn new(token: Option<&str>) -> Result<Self, ConnectError> {
        let header = token
            .map(|token| {
                format!("Bearer {token}")
                    .parse()
                    .map_err(|_| ConnectError::InvalidToken)
            })
            .transpose()?;
        Ok(Self { header })
    }
}

impl Interceptor for Authorization {
    fn call(&mut self, mut request: tonic::Request<()>) -> Result<tonic::Request<()>, Status> {
        if let Some(header) = &self.header {
            request
                .metadata_mut()
                .insert("authorization", header.clone());
        }
        Ok(request)
    }
}
