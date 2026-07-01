package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition

/** How multiple selected tags combine when filtering. */
enum class TagMatch { ANY, ALL }

/**
 * Filters positions by tag. An empty [selectedTagIds] disables filtering (returns all positions).
 * [TagMatch.ANY] keeps positions carrying at least one selected tag; [TagMatch.ALL] keeps only those
 * carrying every selected tag. Untagged positions are excluded whenever the selection is non-empty.
 */
fun filterByTags(
    positions: List<ClosedPosition>,
    selectedTagIds: Set<Long>,
    match: TagMatch,
): List<ClosedPosition> {
    if (selectedTagIds.isEmpty()) return positions
    return positions.filter { p ->
        val ids = p.tags.mapTo(mutableSetOf()) { it.id }
        when (match) {
            TagMatch.ANY -> ids.any { it in selectedTagIds }
            TagMatch.ALL -> ids.containsAll(selectedTagIds)
        }
    }
}
