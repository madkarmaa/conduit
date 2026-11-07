use serde::Serialize;
use serde_json::{Map, Value};

use crate::tool::Reply;

/// Messages turn into protobuf JSON, so compact drops empty values to keep the model's context
/// small. Whole floats print without a fraction, so 99.0 reads as 99.
pub fn compact(value: Value) -> Value {
    match value {
        Value::Array(items) => Value::Array(items.into_iter().map(compact).collect()),
        Value::Object(map) => Value::Object(
            map.into_iter()
                .map(|(key, field)| (key, compact(field)))
                .filter(|(_, field)| !is_empty(field))
                .collect(),
        ),
        Value::Number(number) => Value::Number(whole(number)),
        other => other,
    }
}

fn whole(number: serde_json::Number) -> serde_json::Number {
    match number.as_f64() {
        Some(float) if number.is_f64() && float.fract() == 0.0 && float.abs() < 9e15 => {
            (float as i64).into()
        }
        _ => number,
    }
}

fn is_empty(value: &Value) -> bool {
    match value {
        Value::Null => true,
        Value::String(text) => text.is_empty(),
        Value::Array(items) => items.is_empty(),
        Value::Object(map) => map.is_empty(),
        _ => false,
    }
}

/// The message as protobuf JSON, such as camelCase fields and enums by name.
pub fn to_json(message: &impl Serialize) -> Value {
    serde_json::to_value(message).unwrap_or(Value::Null)
}

pub fn json(value: Value) -> Reply {
    let data = compact(value);
    let text = serde_json::to_string(&data).unwrap_or_else(|_| "{}".to_owned());
    match data {
        Value::Object(map) => Reply {
            text,
            data: Some(map),
        },
        _ => Reply { text, data: None },
    }
}

pub fn message(message: &impl Serialize) -> Reply {
    json(to_json(message))
}

pub fn done(text: impl Into<String>) -> Reply {
    Reply {
        text: text.into(),
        data: None,
    }
}

pub fn lines(header: String, rows: Vec<String>, data: Value) -> Reply {
    let text = std::iter::once(header)
        .chain(rows)
        .collect::<Vec<_>>()
        .join("\n");
    let data = match data {
        Value::Object(map) => Some(map),
        _ => Some(Map::new()),
    };
    Reply { text, data }
}

/// Rounds to two decimals and prints whole numbers without a fraction, like JavaScript.
pub fn number(value: f64) -> String {
    let rounded = (value * 100.0).round() / 100.0;
    if rounded.fract() == 0.0 {
        format!("{}", rounded as i64)
    } else {
        format!("{rounded}")
    }
}

pub fn position(value: Option<&Value>) -> String {
    let Some(value) = value.filter(|value| value.is_object()) else {
        return "?".to_owned();
    };
    ["x", "y", "z"]
        .iter()
        .map(|axis| number(value.get(axis).and_then(Value::as_f64).unwrap_or(0.0)))
        .collect::<Vec<_>>()
        .join(" ")
}

/// One line about an entity, from its protobuf JSON.
pub fn describe_entity(entity: &Value) -> String {
    let text = |key: &str| entity.get(key).and_then(Value::as_str).unwrap_or("");
    let health = entity.get("health").and_then(Value::as_f64);
    let health = health.map_or(String::new(), |health| {
        let max = entity
            .get("maxHealth")
            .and_then(Value::as_f64)
            .unwrap_or(health);
        format!(" {}/{}hp", number(health), number(max))
    });
    let name = entity
        .get("customName")
        .and_then(Value::as_str)
        .filter(|name| !name.is_empty())
        .map_or(String::new(), |name| format!(" \"{name}\""));
    format!(
        "{} {}{name} {} {}{health}",
        text("uuid"),
        text("type"),
        text("dimension"),
        position(entity.get("position"))
    )
}

/// JavaScript's toISOString, for millisecond timestamps.
pub fn iso_time(milliseconds: i64) -> String {
    let seconds = milliseconds.div_euclid(1000);
    let millis = milliseconds.rem_euclid(1000);
    let days = seconds.div_euclid(86_400);
    let second_of_day = seconds.rem_euclid(86_400);
    let (year, month, day) = civil_from_days(days);
    format!(
        "{year:04}-{month:02}-{day:02}T{:02}:{:02}:{:02}.{millis:03}Z",
        second_of_day / 3600,
        second_of_day % 3600 / 60,
        second_of_day % 60
    )
}

/// Howard Hinnant's days-to-civil algorithm.
fn civil_from_days(days: i64) -> (i64, i64, i64) {
    let shifted = days + 719_468;
    let era = shifted.div_euclid(146_097);
    let day_of_era = shifted.rem_euclid(146_097);
    let year_of_era =
        (day_of_era - day_of_era / 1460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let month_index = (5 * day_of_year + 2) / 153;
    let day = day_of_year - (153 * month_index + 2) / 5 + 1;
    let month = if month_index < 10 {
        month_index + 3
    } else {
        month_index - 9
    };
    let year = year_of_era + era * 400 + i64::from(month <= 2);
    (year, month, day)
}
