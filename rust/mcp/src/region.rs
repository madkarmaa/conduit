use std::collections::HashMap;

use conduit_client::proto::BlockRegion;
use serde_json::{Map, json};

use crate::format::to_json;
use crate::tool::Reply;

/// One character per palette entry. Air gets "." so the shape of a build stands out.
const SYMBOLS: &str =
    "#@%&*+=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$!?<>/|~^";
const AIR: &[&str] = &["minecraft:air", "minecraft:cave_air", "minecraft:void_air"];

pub fn summarize_region(region: &BlockRegion) -> Reply {
    let mut counts: HashMap<i32, usize> = HashMap::new();
    for index in &region.blocks {
        *counts.entry(*index).or_default() += 1;
    }
    // First appearance breaks ties, like the stable sort of the TypeScript server.
    let mut order: Vec<i32> = Vec::new();
    for index in &region.blocks {
        if !order.contains(index) {
            order.push(*index);
        }
    }
    order.sort_by_key(|index| std::cmp::Reverse(counts[index]));

    let sorted: Vec<(String, usize)> = order
        .iter()
        .map(|index| (state_name(region, *index), counts[index]))
        .collect();
    let rows = sorted
        .iter()
        .map(|(state, count)| format!("{count} {state}"));
    let mut tally = Map::new();
    for (state, count) in &sorted {
        tally.insert(state.clone(), json!(count));
    }
    let data = json!({ "min": region.min.as_ref().map(to_json), "size": region.size.as_ref().map(to_json), "counts": tally });

    let text = std::iter::once(header(region))
        .chain(rows)
        .collect::<Vec<_>>()
        .join("\n");
    Reply {
        text,
        data: data.as_object().cloned(),
    }
}

pub fn draw_region(region: &BlockRegion) -> Reply {
    let (size_x, size_y, size_z) = region
        .size
        .as_ref()
        .map_or((0, 0, 0), |size| (size.x, size.y, size.z));
    let symbols: Vec<char> = SYMBOLS.chars().collect();
    let marks: Vec<char> = region
        .palette
        .iter()
        .enumerate()
        .map(|(index, state)| {
            if AIR.contains(&base_id(&state.serialized)) {
                '.'
            } else {
                symbols.get(index).copied().unwrap_or('?')
            }
        })
        .collect();
    let legend = region
        .palette
        .iter()
        .zip(&marks)
        .map(|(state, mark)| format!("{mark} {}", state.serialized));

    let mut layers = Vec::new();
    let min_y = region.min.as_ref().map_or(0, |min| min.y);
    for y in 0..size_y {
        let mut rows = Vec::new();
        for z in 0..size_z {
            let start = ((y * size_z + z) * size_x) as usize;
            let row = region.blocks.iter().skip(start).take(size_x as usize);
            rows.push(
                row.map(|index| marks.get(*index as usize).copied().unwrap_or('?'))
                    .collect::<String>(),
            );
        }
        layers.push(format!("y={}\n{}", min_y + y, rows.join("\n")));
    }

    let text = [
        header(region),
        "Rows run along z, columns along x.".to_owned(),
    ]
    .into_iter()
    .chain(legend)
    .chain(layers)
    .collect::<Vec<_>>()
    .join("\n");
    let data = json!({ "min": region.min.as_ref().map(to_json), "size": region.size.as_ref().map(to_json) });
    Reply {
        text,
        data: data.as_object().cloned(),
    }
}

pub fn palette_fits(region: &BlockRegion) -> bool {
    region.palette.len() <= SYMBOLS.chars().count()
}

fn header(region: &BlockRegion) -> String {
    let axis = |value: Option<i32>| value.map_or("undefined".to_owned(), |value| value.to_string());
    let min = region.min.as_ref();
    let size = region.size.as_ref();
    format!(
        "Region from {} {} {}, size {}x{}x{} (x, y, z)",
        axis(min.map(|min| min.x)),
        axis(min.map(|min| min.y)),
        axis(min.map(|min| min.z)),
        axis(size.map(|size| size.x)),
        axis(size.map(|size| size.y)),
        axis(size.map(|size| size.z)),
    )
}

fn state_name(region: &BlockRegion, index: i32) -> String {
    region
        .palette
        .get(index as usize)
        .map_or("unknown".to_owned(), |state| state.serialized.clone())
}

fn base_id(serialized: &str) -> &str {
    serialized.split(['[', '{']).next().unwrap_or(serialized)
}
