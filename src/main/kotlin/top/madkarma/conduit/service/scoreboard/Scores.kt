package top.madkarma.conduit.service.scoreboard

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import top.madkarma.conduit.game.*

@Serializable
data class ObjectiveInfo(
    val name: String,
    val displayName: String,
    /** What counts the score, such as "dummy" or "minecraft.mined:minecraft.stone". */
    val criterion: String,
    /** Where the objective shows, such as "sidebar". */
    val displaySlots: List<String>,
)

@Serializable data class ScoreInfo(val holder: String, val objective: String, val value: Int)

internal fun MinecraftServer.listObjectives(): List<ObjectiveInfo> =
    scoreboard.objectives.map { objective ->
        ObjectiveInfo(
            name = objective.name,
            displayName = objective.displayName.string,
            criterion = objective.criteria.name,
            displaySlots =
                DisplaySlot.entries
                    .filter { scoreboard.getDisplayObjective(it) == objective }
                    .map { it.serializedName },
        )
    }

/** The scores of one holder, or of one objective, or of a holder in one objective. */
internal fun MinecraftServer.readScores(objective: String?, holder: String?): List<ScoreInfo> {
    if (holder != null) {
        return scoreboard
            .listPlayerScores(ScoreHolder.forNameOnly(holder))
            .map { (scored, value) -> ScoreInfo(holder, scored.name, value) }
            .filter { objective == null || it.objective == objective }
    }

    val scored = objective(objective ?: invalidInput("Give objective or holder"))
    return scoreboard
        .listPlayerScores(scored)
        .filterNot { it.isHidden }
        .map { ScoreInfo(it.owner(), scored.name, it.value()) }
}

internal fun MinecraftServer.addObjective(name: String, criterion: String, displayName: String?) {
    if (scoreboard.getObjective(name) != null) failedPrecondition("Objective $name already exists")

    val criteria =
        ObjectiveCriteria.byName(criterion).getOrNull()
            ?: invalidInput("Unknown criterion: $criterion")
    val title = textComponent(displayName ?: name, registryAccess())
    scoreboard.addObjective(name, criteria, title, criteria.defaultRenderType, false, null)
}

internal fun MinecraftServer.removeObjective(name: String) {
    scoreboard.removeObjective(objective(name))
}

/** Sets or adds to a score, and returns the new value. */
internal fun MinecraftServer.changeScore(
    holder: String,
    objective: String,
    value: Int,
    add: Boolean,
): Int {
    val scored = objective(objective)
    if (scored.criteria.isReadOnly) failedPrecondition("The game sets the scores of $objective")

    val score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), scored)
    if (add) return score.add(value)

    score.set(value)
    return value
}

/** Resets a holder's score in one objective, or in every objective. */
internal fun MinecraftServer.resetScore(holder: String, objective: String?) {
    val scoreHolder = ScoreHolder.forNameOnly(holder)
    if (objective == null) scoreboard.resetAllPlayerScores(scoreHolder)
    else scoreboard.resetSinglePlayerScore(scoreHolder, objective(objective))
}

/** Shows an objective in a display slot, or clears the slot without one. */
internal fun MinecraftServer.displayObjective(slot: String, objective: String?) {
    val displaySlot =
        DisplaySlot.entries.firstOrNull { it.serializedName == slot }
            ?: invalidInput("Unknown slot: $slot")
    scoreboard.setDisplayObjective(displaySlot, objective?.let { objective(it) })
}

private fun MinecraftServer.objective(name: String): Objective =
    scoreboard.getObjective(name) ?: notFound("Unknown objective: $name")
