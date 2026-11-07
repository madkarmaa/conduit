use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::format::done;
use crate::schemas::{Vec3, one, to_text};
use crate::tool::{ToolResult, ToolSpec, require_fields, required};

#[derive(Deserialize, JsonSchema)]
pub struct NotifyInput {
    action: Option<String>,
    /// Required for title and sound.
    player: Option<String>,
    /// Message, or the title for title.
    message: Option<String>,
    /// Plain text, or a JSON text component when it starts with { or [.
    subtitle: Option<String>,
    /// Show message in the action bar.
    #[serde(default)]
    action_bar: bool,
    /// Sound id, e.g. "minecraft:entity.player.levelup".
    sound: Option<String>,
    /// Sound position. Defaults to the player.
    position: Option<Vec3>,
    #[serde(default = "full_volume")]
    #[schemars(range(min = 0))]
    volume: f32,
    #[serde(default = "full_volume")]
    #[schemars(range(min = 0.5, max = 2))]
    pitch: f32,
}

fn full_volume() -> f32 {
    one() as f32
}

pub fn notify() -> ToolSpec<NotifyInput> {
    ToolSpec {
        name: "notify",
        title: "Notify players",
        description: "message sends a chat or action bar message to a player, or to everyone when \
            no player is given. title shows a title and subtitle to a player. sound plays a sound \
            to a player."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("message", Write, send_message),
            action!("title", Write, title),
            action!("sound", Write, sound),
        ],
    }
}

async fn send_message(context: Arc<Context>, input: NotifyInput) -> ToolResult {
    let text = required(input.message, "message")?;
    let message = Some(to_text(text));
    match input.player {
        Some(player) => {
            let request = proto::SendMessageRequest {
                player,
                message,
                overlay: input.action_bar,
            };
            context.clients.player.clone().send_message(request).await?;
        }
        None => {
            let request = proto::BroadcastRequest {
                message,
                overlay: input.action_bar,
            };
            context.clients.server.clone().broadcast(request).await?;
        }
    }
    Ok(done("Message sent"))
}

async fn title(context: Arc<Context>, input: NotifyInput) -> ToolResult {
    let player = required(input.player, "player")?;
    let request = proto::SendTitleRequest {
        player,
        title: input.message.map(to_text),
        subtitle: input.subtitle.map(to_text),
        ..Default::default()
    };
    context.clients.player.clone().send_title(request).await?;
    Ok(done("Title shown"))
}

async fn sound(context: Arc<Context>, input: NotifyInput) -> ToolResult {
    require_fields(&[
        ("player", input.player.is_some()),
        ("sound", input.sound.is_some()),
    ])?;
    let request = proto::PlaySoundRequest {
        player: input.player.unwrap_or_default(),
        sound: input.sound.unwrap_or_default(),
        position: input.position.map(Into::into),
        volume: input.volume,
        pitch: input.pitch,
        category: String::new(),
    };
    context.clients.player.clone().play_sound(request).await?;
    Ok(done("Sound played"))
}
