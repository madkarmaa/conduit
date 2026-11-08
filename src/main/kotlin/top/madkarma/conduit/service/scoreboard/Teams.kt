package top.madkarma.conduit.service.scoreboard

import java.util.*
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import net.minecraft.world.scores.TeamColor
import top.madkarma.conduit.game.*

@Serializable
data class TeamInfo(
    val name: String,
    val displayName: String,
    val color: String?,
    val friendlyFire: Boolean,
    val nameTagVisibility: String,
    val collision: String,
    val members: List<String>,
)

/** Fields left null stay as they are. A color of "reset" removes the team's color. */
class TeamChange(
    val displayName: String? = null,
    val color: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val friendlyFire: Boolean? = null,
    val nameTagVisibility: String? = null,
    val collision: String? = null,
)

internal fun MinecraftServer.listTeams(): List<TeamInfo> =
    scoreboard.playerTeams.map { team ->
        TeamInfo(
            name = team.name,
            displayName = team.displayName.string,
            color = team.color.map { it.serializedName }.orElse(null),
            friendlyFire = team.isAllowFriendlyFire,
            nameTagVisibility = team.nameTagVisibility.name.lowercase(),
            collision = team.collisionRule.name.lowercase(),
            members = team.players.sorted(),
        )
    }

internal fun MinecraftServer.createTeam(name: String, change: TeamChange) {
    if (scoreboard.getPlayerTeam(name) != null) failedPrecondition("Team $name already exists")
    applyChange(scoreboard.addPlayerTeam(name), change)
}

internal fun MinecraftServer.modifyTeam(name: String, change: TeamChange) {
    applyChange(team(name), change)
}

internal fun MinecraftServer.removeTeam(name: String) {
    scoreboard.removePlayerTeam(team(name))
}

/** Members are player names or entity UUIDs. A member leaves any team it was on before. */
internal fun MinecraftServer.joinTeam(name: String, members: List<String>) {
    val team = team(name)
    members.forEach { scoreboard.addPlayerToTeam(it, team) }
}

internal fun MinecraftServer.leaveTeams(members: List<String>) {
    members.forEach { scoreboard.removePlayerFromTeam(it) }
}

private fun MinecraftServer.applyChange(team: PlayerTeam, change: TeamChange) {
    val registries = registryAccess()

    change.displayName?.let { team.displayName = textComponent(it, registries) }
    change.prefix?.let { team.setPlayerPrefix(textComponent(it, registries)) }
    change.suffix?.let { team.setPlayerSuffix(textComponent(it, registries)) }
    change.friendlyFire?.let { team.isAllowFriendlyFire = it }
    change.color?.let { team.color = teamColor(it) }
    change.nameTagVisibility?.let {
        team.nameTagVisibility = enumByName(it, Team.Visibility.ALWAYS, "name tag visibility")
    }
    change.collision?.let {
        team.collisionRule = enumByName(it, Team.CollisionRule.ALWAYS, "collision")
    }
}

private fun teamColor(name: String): Optional<TeamColor> {
    if (name == "reset") return Optional.empty()
    return Optional.of(TeamColor.byName(name) ?: invalidInput("Unknown team color: $name"))
}

private fun MinecraftServer.team(name: String): PlayerTeam =
    scoreboard.getPlayerTeam(name) ?: notFound("Unknown team: $name")
