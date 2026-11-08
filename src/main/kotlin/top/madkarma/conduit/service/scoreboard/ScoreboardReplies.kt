package top.madkarma.conduit.service.scoreboard

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.mcp.*

internal fun MinecraftServer.scoreReply(input: ToolInput, add: Boolean): Reply {
    val holder = input.string("holder")
    val objective = input.string("objective")
    val value = changeScore(holder, objective, input.int("value"), add)
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
