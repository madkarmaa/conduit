use conduit_client::proto;
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Deserialize, Serialize, JsonSchema)]
pub struct BlockPos {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}

impl From<BlockPos> for proto::BlockPos {
    fn from(pos: BlockPos) -> Self {
        Self {
            x: pos.x,
            y: pos.y,
            z: pos.z,
        }
    }
}

#[derive(Clone, Copy, Deserialize, Serialize, JsonSchema)]
pub struct Vec3 {
    pub x: f64,
    pub y: f64,
    pub z: f64,
}

impl From<Vec3> for proto::Vec3 {
    fn from(vector: Vec3) -> Self {
        Self {
            x: vector.x,
            y: vector.y,
            z: vector.z,
        }
    }
}

/// Degrees. Yaw 0 faces south and pitch -90 faces up.
#[derive(Clone, Copy, Deserialize, Serialize, JsonSchema)]
pub struct Rotation {
    pub yaw: f32,
    pub pitch: f32,
}

impl From<Rotation> for proto::Rotation {
    fn from(rotation: Rotation) -> Self {
        Self {
            yaw: rotation.yaw,
            pitch: rotation.pitch,
        }
    }
}

#[derive(Clone, Deserialize, Serialize, JsonSchema)]
pub struct Item {
    /// Item id, e.g. "minecraft:diamond_sword".
    pub id: String,
    #[serde(default = "one")]
    #[schemars(range(min = 1))]
    pub count: i32,
    /// Data component patch as SNBT, e.g. {"minecraft:custom_name":"Excalibur"}.
    pub components: Option<String>,
}

impl From<Item> for proto::ItemStack {
    fn from(item: Item) -> Self {
        Self {
            id: item.id,
            count: item.count,
            components: item.components.unwrap_or_default(),
        }
    }
}

pub fn one() -> i32 {
    1
}

pub fn yes() -> bool {
    true
}

pub fn default_limit() -> i32 {
    50
}

/// Plain text, or a JSON text component when it starts with { or [.
pub fn to_text(value: String) -> proto::Text {
    use proto::text::Content;
    let trimmed = value.trim_start();
    let content = if trimmed.starts_with('{') || trimmed.starts_with('[') {
        Content::Json(value)
    } else {
        Content::Plain(value)
    };
    proto::Text {
        content: Some(content),
    }
}

pub fn dimension(value: Option<String>) -> String {
    value.unwrap_or_default()
}

pub fn player_ref(player: String) -> proto::PlayerRef {
    proto::PlayerRef { player }
}

pub fn entity_ref(uuid: String) -> proto::EntityRef {
    proto::EntityRef { uuid }
}
