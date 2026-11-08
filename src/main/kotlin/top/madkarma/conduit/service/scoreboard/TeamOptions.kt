package top.madkarma.conduit.service.scoreboard

import net.minecraft.world.scores.TeamColor
import top.madkarma.conduit.mcp.*

// The input fields that create_team and modify_team share.

private val TEAM_COLORS = TeamColor.entries.map { it.serializedName } + "reset"

internal fun InputBuilder.teamFields() {
    optional("color", enumSchema(TEAM_COLORS, "Team color. reset removes it."))
    optional("prefix", TEXT.described("Shown before member names."))
    optional("suffix", TEXT.described("Shown after member names."))
    optional("friendly_fire", booleanSchema("Whether members can hurt each other."))
    optional(
        "name_tag_visibility",
        enumSchema(listOf("always", "never", "hide_for_other_teams", "hide_for_own_team")),
    )
    optional(
        "collision",
        enumSchema(listOf("always", "never", "push_other_teams", "push_own_team")),
    )
}

internal fun teamChange(input: ToolInput) =
    TeamChange(
        displayName = input.stringOrNull("display_name"),
        color = input.stringOrNull("color"),
        prefix = input.stringOrNull("prefix"),
        suffix = input.stringOrNull("suffix"),
        friendlyFire = input.booleanOrNull("friendly_fire"),
        nameTagVisibility = input.stringOrNull("name_tag_visibility"),
        collision = input.stringOrNull("collision"),
    )
