//! The edit_entity actions that change health, damage, effects and attributes.

use std::sync::Arc;

use conduit_client::proto;

use super::edit_entity::EditInput;
use crate::Context;
use crate::format::done;
use crate::tool::{ToolResult, require_fields, required};

pub(super) async fn set_health(context: Arc<Context>, input: EditInput) -> ToolResult {
    let amount = required(input.amount, "amount")?;
    context
        .clients
        .entity
        .clone()
        .set_health(proto::SetHealthRequest {
            uuid: input.uuid,
            health: amount,
        })
        .await?;
    Ok(done(format!(
        "Health set to {}",
        crate::format::number(f64::from(amount))
    )))
}

pub(super) async fn damage(context: Arc<Context>, input: EditInput) -> ToolResult {
    let amount = required(input.amount, "amount")?;
    let request = proto::DamageRequest {
        uuid: input.uuid,
        amount,
        damage_type: input.damage_type,
        attacker: input.attacker,
    };
    context.clients.entity.clone().damage(request).await?;
    Ok(done(format!(
        "Dealt {} damage",
        crate::format::number(f64::from(amount))
    )))
}

pub(super) async fn add_effect(context: Arc<Context>, input: EditInput) -> ToolResult {
    let effect = required(input.effect, "effect")?;
    let request = proto::AddEffectRequest {
        uuid: input.uuid,
        effect: Some(proto::StatusEffect {
            id: effect.clone(),
            duration: input.duration_ticks,
            amplifier: input.amplifier,
            ambient: false,
            visible: input.show_particles,
            show_icon: true,
        }),
    };
    context.clients.entity.clone().add_effect(request).await?;
    Ok(done(format!("Added {effect}")))
}

pub(super) async fn remove_effect(context: Arc<Context>, input: EditInput) -> ToolResult {
    let request = proto::RemoveEffectRequest {
        uuid: input.uuid,
        effect: input.effect.unwrap_or_default(),
    };
    context
        .clients
        .entity
        .clone()
        .remove_effect(request)
        .await?;
    Ok(done("Effects removed"))
}

pub(super) async fn set_attribute(context: Arc<Context>, input: EditInput) -> ToolResult {
    require_fields(&[
        ("attribute", input.attribute.is_some()),
        ("base", input.base.is_some()),
    ])?;
    let (attribute, base) = (
        input.attribute.unwrap_or_default(),
        input.base.unwrap_or_default(),
    );
    let text = format!("{attribute} base set to {}", crate::format::number(base));
    let request = proto::SetAttributeBaseRequest {
        uuid: input.uuid,
        attribute,
        base,
    };
    context
        .clients
        .entity
        .clone()
        .set_attribute_base(request)
        .await?;
    Ok(done(text))
}
