use std::collections::VecDeque;
use std::sync::{Arc, Mutex};

use conduit_client::AuthorizedChannel;
use conduit_client::proto::SubscribeRequest;
use conduit_client::proto::event_service_client::EventServiceClient;
use serde_json::Value;
use tokio::sync::Notify;
use tokio::time::Instant;

use crate::event_types::{EventQuery, SUBSCRIBED_TYPES, matches, snake_case};
use crate::tool::ToolError;

const BUFFER_SIZE: usize = 1000;

#[derive(Clone)]
pub struct BufferedEvent {
    pub cursor: u64,
    pub name: String,
    pub tick: i64,
    pub payload: Value,
}

pub struct EventPage {
    pub events: Vec<BufferedEvent>,
    pub next_cursor: u64,
    /// Events newer than the query's cursor that left the buffer before they were read.
    pub expired: u64,
}

#[derive(Default)]
struct FeedState {
    buffer: VecDeque<BufferedEvent>,
    last_cursor: u64,
    running: bool,
    failure: Option<(&'static str, String)>,
}

/// Keeps one subscription open and numbers its events, so agents can poll with a cursor.
pub struct EventFeed {
    client: EventServiceClient<AuthorizedChannel>,
    state: Arc<Mutex<FeedState>>,
    changed: Arc<Notify>,
}

impl EventFeed {
    pub fn new(client: EventServiceClient<AuthorizedChannel>) -> Self {
        Self {
            client,
            state: Arc::default(),
            changed: Arc::new(Notify::new()),
        }
    }

    pub async fn poll(&self, query: EventQuery) -> Result<EventPage, ToolError> {
        self.subscribe();
        let deadline = Instant::now() + query.timeout;

        loop {
            // Waiting starts before the check, so an event between the two still wakes it.
            let changed = self.changed.notified();
            {
                let state = self.state.lock().expect("event feed lock");
                let matching = matching(&state, &query);
                if !matching.is_empty() || state.failure.is_some() || Instant::now() >= deadline {
                    if let (true, Some((status, details))) = (matching.is_empty(), &state.failure) {
                        return Err(ToolError::Grpc {
                            status,
                            details: details.clone(),
                        });
                    }
                    return Ok(page(&state, &query, matching));
                }
            }
            let _ = tokio::time::timeout_at(deadline, changed).await;
        }
    }

    fn subscribe(&self) {
        {
            let mut state = self.state.lock().expect("event feed lock");
            if state.running {
                return;
            }
            state.running = true;
            state.failure = None;
        }

        let mut client = self.client.clone();
        let state = Arc::clone(&self.state);
        let changed = Arc::clone(&self.changed);
        tokio::spawn(async move {
            let request = SubscribeRequest {
                types: SUBSCRIBED_TYPES.iter().map(|kind| *kind as i32).collect(),
                ..Default::default()
            };
            let failure = match client.subscribe(request).await {
                Err(status) => ToolError::from(status),
                Ok(response) => {
                    let mut stream = response.into_inner();
                    loop {
                        match stream.message().await {
                            Ok(Some(event)) => add(&state, &changed, &event),
                            Ok(None) => {
                                break ToolError::Grpc {
                                    status: "UNAVAILABLE",
                                    details: "Event stream ended".to_owned(),
                                };
                            }
                            Err(status) => break ToolError::from(status),
                        }
                    }
                }
            };

            let mut locked = state.lock().expect("event feed lock");
            locked.running = false;
            if let ToolError::Grpc { status, details } = failure {
                locked.failure.get_or_insert((status, details));
            }
            drop(locked);
            changed.notify_waiters();
        });
    }
}

fn add(state: &Mutex<FeedState>, changed: &Notify, event: &conduit_client::proto::Event) {
    let json = serde_json::to_value(event).unwrap_or(Value::Null);
    let (name, payload) = json
        .as_object()
        .and_then(|fields| {
            fields
                .iter()
                .find(|(key, _)| !matches!(key.as_str(), "timestampMs" | "tick" | "sequence"))
        })
        .map(|(key, payload)| (snake_case(key), payload.clone()))
        .unwrap_or_else(|| ("unknown".to_owned(), Value::Object(Default::default())));

    let mut locked = state.lock().expect("event feed lock");
    locked.last_cursor += 1;
    let cursor = locked.last_cursor;
    locked.buffer.push_back(BufferedEvent {
        cursor,
        name,
        tick: i64::from(event.tick),
        payload,
    });
    if locked.buffer.len() > BUFFER_SIZE {
        locked.buffer.pop_front();
    }
    drop(locked);
    changed.notify_waiters();
}

fn matching(state: &FeedState, query: &EventQuery) -> Vec<BufferedEvent> {
    state
        .buffer
        .iter()
        .filter(|event| matches(event, query))
        .cloned()
        .collect()
}

fn page(state: &FeedState, query: &EventQuery, matching: Vec<BufferedEvent>) -> EventPage {
    let events: Vec<BufferedEvent> = matching.into_iter().take(query.limit).collect();
    let oldest = state
        .buffer
        .front()
        .map_or(state.last_cursor + 1, |event| event.cursor);
    EventPage {
        next_cursor: events
            .last()
            .map_or(state.last_cursor, |event| event.cursor),
        expired: oldest.saturating_sub(query.after + 1),
        events,
    }
}
