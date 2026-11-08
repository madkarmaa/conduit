package top.madkarma.conduit.service.scoreboard

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.mcp.*

private val TEAM_COLORS =
    listOf(
        "black",
        "dark_blue",
        "dark_green",
        "dark_aqua",
        "dark_red",
        "dark_purple",
        "gold",
        "gray",
        "dark_gray",
        "blue",
        "green",
        "aqua",
        "red",
        "light_purple",
        "yellow",
        "white",
        "reset",
    )

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

internal fun changeScore(server: MinecraftServer, input: ToolInput, add: Boolean): Reply {
    val holder = input.string("holder")
    val objective = input.string("objective")
    val value = server.changeScore(holder, objective, input.int("value"), add)
    return done("$holder $objective = $value")
}

internal fun objectivesReply(objectives: List<ObjectiveInfo>): Reply =
    lines(
        "${objectives.size} objectives",
        objectives.map {
            val shown =
                if (it.displaySlots.isEmpty()) ""
                else ", shown in ${it.displaySlots.joinToString(", ")}"
            "${it.name} \"${it.displayName}\" (${it.criterion})$shown"
        },
        dataOf("objectives" to encoded(objectives)),
    )

internal fun teamsReply(teams: List<TeamInfo>): Reply =
    lines(
        "${teams.size} teams",
        teams.map {
            "${it.name} \"${it.displayName}\" ${it.color ?: "no color"}: ${it.members.joinToString(", ")}"
        },
        dataOf("teams" to encoded(teams)),
    )
