package top.madkarma.conduit.service.player

import kotlin.jvm.optionals.getOrNull
import kotlinx.serialization.Serializable
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import top.madkarma.conduit.game.*

@Serializable
data class AdvancementStatus(
    val id: String,
    val title: String,
    val done: Boolean,
    val criteriaDone: Int,
    val criteriaTotal: Int,
    /** The criteria left to do, such as the biomes of "Adventuring Time". */
    val missing: List<String>,
)

/**
 * An online player's advancements whose id or title contains filter. Status is all, done or undone.
 * Recipe unlocks and hidden helper advancements without a display are left out.
 */
internal fun MinecraftServer.advancementsOf(
    ref: String,
    filter: String,
    status: String,
): List<AdvancementStatus> {
    val player = player(ref)

    return advancements.allAdvancements
        .asSequence()
        .filter { it.value().display().isPresent && !it.id().path.startsWith("recipes/") }
        .map { statusOf(player, it) }
        .filter { it.id.contains(filter, ignoreCase = true) || looselyContains(it.title, filter) }
        .filter { status == "all" || it.done == (status == "done") }
        .sortedBy { it.id }
        .toList()
}

/** Grants one criterion, or every criterion left. Returns how many it granted. */
internal fun MinecraftServer.grantAdvancement(ref: String, id: String, criterion: String?): Int {
    val player = player(ref)
    val advancement = advancement(id)
    val criteria =
        criterion?.let { listOf(checkedCriterion(advancement, it)) }
            ?: player.advancements.getOrStartProgress(advancement).remainingCriteria.toList()

    EditJournal.before { AdvancementChange.before(player, advancement, criteria, granting = true) }
    return criteria.count { player.advancements.award(advancement, it) }
}

/** Revokes one criterion, or every criterion done. Returns how many it revoked. */
internal fun MinecraftServer.revokeAdvancement(ref: String, id: String, criterion: String?): Int {
    val player = player(ref)
    val advancement = advancement(id)
    val criteria =
        criterion?.let { listOf(checkedCriterion(advancement, it)) }
            ?: player.advancements.getOrStartProgress(advancement).completedCriteria.toList()

    EditJournal.before { AdvancementChange.before(player, advancement, criteria, granting = false) }
    return criteria.count { player.advancements.revoke(advancement, it) }
}

private fun statusOf(player: ServerPlayer, advancement: AdvancementHolder): AdvancementStatus {
    val progress = player.advancements.getOrStartProgress(advancement)
    val done = progress.completedCriteria.count()
    val missing = progress.remainingCriteria.toList()

    return AdvancementStatus(
        id = advancement.id().toString(),
        title = advancement.value().display().getOrNull()?.title?.string.orEmpty(),
        done = progress.isDone,
        criteriaDone = done,
        criteriaTotal = done + missing.size,
        missing = if (progress.isDone) emptyList() else missing,
    )
}

/** The titles of the advancements that have one, by id. */
internal fun MinecraftServer.advancementTitles(): Map<String, String> =
    advancements.allAdvancements
        .mapNotNull { holder ->
            holder.value().display().getOrNull()?.let { holder.id().toString() to it.title.string }
        }
        .toMap()

/**
 * Whether a title contains filter, ignoring case and punctuation, so "how_did" matches "How Did We
 * Get Here?".
 */
internal fun looselyContains(title: String, filter: String): Boolean =
    loose(title).contains(loose(filter))

private fun loose(text: String): String = text.lowercase().replace(NOT_LETTERS, " ").trim()

private val NOT_LETTERS = Regex("[^\\p{L}\\p{N}]+")

// Agents often guess an id from the title, so a matching title names the real id.
private fun MinecraftServer.advancement(id: String): AdvancementHolder {
    advancements.get(parseId(id))?.let {
        return it
    }

    val name = id.substringAfterLast(':').substringAfterLast('/')
    val match = advancementTitles().entries.firstOrNull { loose(it.value) == loose(name) }
    val hint = match?.let { ". \"${it.value}\" is ${it.key}" }.orEmpty()
    notFound("Unknown advancement: $id$hint")
}

private fun checkedCriterion(advancement: AdvancementHolder, criterion: String): String {
    if (criterion !in advancement.value().criteria()) {
        invalidInput("${advancement.id()} has no criterion $criterion")
    }
    return criterion
}
