// Input structs keep fields that only feed their schema or select the action.
#![allow(dead_code)]

mod batch;
mod blocks;
mod edit_entity;
mod edit_player;
mod entities;
mod entity_status;
mod environment;
mod events;
mod get_player;
mod inventory;
mod list_players;
mod movement;
mod notify;
mod recipes;
mod regions;
mod registry;
mod server;
mod server_info;
mod spawning;
mod structures;
mod terrain;
mod world;
mod world_info;

use std::sync::Arc;

use crate::policy::ToolPolicy;
use crate::tool::{OfferedTool, offer};

/// Every tool the policy offers, in the order of the TypeScript server.
pub fn all(policy: &Arc<ToolPolicy>) -> Vec<Box<dyn OfferedTool>> {
    [
        offer(server_info::info(), policy),
        offer(server::command(), policy),
        offer(server::admin(), policy),
        offer(blocks::get_block(), policy),
        offer(blocks::edit_blocks(), policy),
        offer(regions::get_region(), policy),
        offer(terrain::terrain(), policy),
        offer(world_info::world_info(), policy),
        offer(world::world_settings(), policy),
        offer(environment::game_rules(), policy),
        offer(environment::world_border(), policy),
        offer(environment::explode(), policy),
        offer(entities::list(), policy),
        offer(entities::get(), policy),
        offer(spawning::spawn(), policy),
        offer(spawning::remove(), policy),
        offer(movement::movement(), policy),
        offer(edit_entity::edit(), policy),
        offer(list_players::list(), policy),
        offer(get_player::get(), policy),
        offer(inventory::inventory_tool(), policy),
        offer(inventory::give(), policy),
        offer(inventory::offline(), policy),
        offer(edit_player::edit(), policy),
        offer(notify::notify(), policy),
        offer(registry::registry(), policy),
        offer(recipes::recipes(), policy),
        offer(structures::structures(), policy),
        offer(batch::batch(), policy),
        offer(events::wait_for_events(), policy),
    ]
    .into_iter()
    .flatten()
    .collect()
}
