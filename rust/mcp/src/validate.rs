use serde_json::{Map, Value};

/// Checks the limits that the input schema declares: number ranges, string and array lengths and
/// enums. Parsing the input into its struct already checks the types and required fields.
pub fn check(schema: &Map<String, Value>, value: &Value, path: &str) -> Result<(), String> {
    let limit = |key: &str| schema.get(key).and_then(Value::as_f64);
    let at = |problem: String| {
        if path.is_empty() {
            problem
        } else {
            format!("{path}: {problem}")
        }
    };

    if let Some(allowed) = schema.get("enum").and_then(Value::as_array)
        && !allowed.contains(value)
    {
        return Err(at(format!(
            "must be one of {}",
            Value::Array(allowed.clone())
        )));
    }

    match value {
        Value::Number(number) => {
            let number = number.as_f64().unwrap_or_default();
            if let Some(minimum) = limit("minimum").filter(|minimum| number < *minimum) {
                return Err(at(format!("must be at least {minimum}")));
            }
            if let Some(maximum) = limit("maximum").filter(|maximum| number > *maximum) {
                return Err(at(format!("must be at most {maximum}")));
            }
        }
        Value::String(text) => {
            let length = text.chars().count() as f64;
            if let Some(minimum) = limit("minLength").filter(|minimum| length < *minimum) {
                return Err(at(format!("must have at least {minimum} characters")));
            }
            if let Some(maximum) = limit("maxLength").filter(|maximum| length > *maximum) {
                return Err(at(format!("must have at most {maximum} characters")));
            }
        }
        Value::Array(items) => {
            let length = items.len() as f64;
            if let Some(minimum) = limit("minItems").filter(|minimum| length < *minimum) {
                return Err(at(format!("must have at least {minimum} items")));
            }
            if let Some(maximum) = limit("maxItems").filter(|maximum| length > *maximum) {
                return Err(at(format!("must have at most {maximum} items")));
            }
            if let Some(Value::Object(item_schema)) = schema.get("items") {
                for (index, item) in items.iter().enumerate() {
                    check(item_schema, item, &format!("{path}[{index}]"))?;
                }
            }
        }
        Value::Object(fields) => {
            if let Some(Value::Object(properties)) = schema.get("properties") {
                for (name, field) in fields {
                    if let Some(Value::Object(field_schema)) = properties.get(name) {
                        let field_path = if path.is_empty() {
                            name.clone()
                        } else {
                            format!("{path}.{name}")
                        };
                        check(field_schema, field, &field_path)?;
                    }
                }
            }
        }
        _ => {}
    }
    Ok(())
}
