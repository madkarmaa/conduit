use std::sync::Arc;

use conduit_client::proto;
use schemars::JsonSchema;
use serde::Deserialize;

use crate::Context;
use crate::action;
use crate::format::done;
use crate::schemas::{Item, player_ref};
use crate::tool::{ToolResult, ToolSpec, required};

#[derive(Deserialize, JsonSchema)]
pub struct InventoryInput {
    action: Option<String>,
    /// Player name or UUID.
    player: String,
    /// Required for set_slot and select_slot.
    #[schemars(range(min = 0, max = 40))]
    slot: Option<i32>,
    /// For set_slot. Leave it out to empty the slot.
    item: Option<Item>,
}

#[derive(Deserialize, JsonSchema)]
pub struct GiveInput {
    /// Player name or UUID.
    player: String,
    item: Item,
}

#[derive(Deserialize, JsonSchema)]
pub struct OfflineInput {
    action: Option<String>,
    /// Player name or UUID.
    player: String,
    /// SNBT to merge. Required for merge_nbt.
    snbt: Option<String>,
    /// Required for set_slot.
    #[schemars(range(min = 0, max = 40))]
    slot: Option<i32>,
    /// For set_slot. Leave it out to empty the slot.
    item: Option<Item>,
}

pub fn inventory_tool() -> ToolSpec<InventoryInput> {
    ToolSpec {
        name: "inventory",
        title: "Edit inventory",
        description: "Edits an online player's inventory. set_slot puts an item in a slot or \
            empties it. clear empties the inventory. select_slot picks the held hotbar slot. Slots \
            0-8 are the hotbar, 9-35 the main inventory, 36-39 armor (feet, legs, chest, head) and \
            40 the offhand."
            .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("set_slot", Write, set_slot),
            action!("clear", Destructive, clear),
            action!("select_slot", Write, select_slot),
        ],
    }
}

async fn set_slot(context: Arc<Context>, input: InventoryInput) -> ToolResult {
    let slot = required(input.slot, "slot")?;
    let request = proto::SetInventorySlotRequest {
        player: input.player,
        slot,
        item: input.item.map(Into::into),
    };
    context
        .clients
        .player
        .clone()
        .set_inventory_slot(request)
        .await?;
    Ok(done(format!("Slot {slot} set")))
}

async fn clear(context: Arc<Context>, input: InventoryInput) -> ToolResult {
    context
        .clients
        .player
        .clone()
        .clear_inventory(player_ref(input.player))
        .await?;
    Ok(done("Inventory cleared"))
}

async fn select_slot(context: Arc<Context>, input: InventoryInput) -> ToolResult {
    let slot = required(input.slot, "slot")?;
    context
        .clients
        .player
        .clone()
        .set_selected_slot(proto::SetSelectedSlotRequest {
            player: input.player,
            slot,
        })
        .await?;
    Ok(done(format!("Selected hotbar slot {slot}")))
}

pub fn give() -> ToolSpec<GiveInput> {
    ToolSpec {
        name: "give_item",
        title: "Give item",
        description:
            "Gives an item stack to an online player. Items that do not fit drop at their feet."
                .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![action!("give", Write, give_item)],
    }
}

async fn give_item(context: Arc<Context>, input: GiveInput) -> ToolResult {
    let text = format!("Gave {} {}", input.item.count, input.item.id);
    let request = proto::GiveItemRequest {
        player: input.player,
        item: Some(input.item.into()),
    };
    let response = context
        .clients
        .player
        .clone()
        .give_item(request)
        .await?
        .into_inner();
    let dropped = if response.dropped > 0 {
        format!(", {} dropped", response.dropped)
    } else {
        String::new()
    };
    Ok(done(format!("{text}{dropped}")))
}

pub fn offline() -> ToolSpec<OfflineInput> {
    ToolSpec {
        name: "offline_player",
        title: "Edit saved player data",
        description:
            "Edits the saved data of an offline player, which applies at their next login. \
            merge_nbt merges SNBT into it. set_slot sets an inventory slot like the inventory \
            tool. get_player reads saved data."
                .into(),
        default_action: None,
        sees_other_players: false,
        meta: None,
        actions: vec![
            action!("merge_nbt", Destructive, merge_offline),
            action!("set_slot", Destructive, set_offline_slot),
        ],
    }
}

async fn merge_offline(context: Arc<Context>, input: OfflineInput) -> ToolResult {
    let snbt = required(input.snbt, "snbt")?;
    let request = proto::MergeOfflinePlayerNbtRequest {
        player: input.player,
        snbt,
    };
    context
        .clients
        .player
        .clone()
        .merge_offline_player_nbt(request)
        .await?;
    Ok(done("Saved data merged"))
}

async fn set_offline_slot(context: Arc<Context>, input: OfflineInput) -> ToolResult {
    let slot = required(input.slot, "slot")?;
    let request = proto::SetInventorySlotRequest {
        player: input.player,
        slot,
        item: input.item.map(Into::into),
    };
    context
        .clients
        .player
        .clone()
        .set_offline_inventory_slot(request)
        .await?;
    Ok(done(format!("Slot {slot} set")))
}
