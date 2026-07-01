package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag

/** Per-tag performance. [tag] is null for the "Untagged" group. */
data class TagStat(val tag: Tag?, val metrics: DashboardMetrics)

/**
 * Groups positions by tag and computes [DashboardMetrics] per group. A position carrying N tags
 * counts toward all N groups, so per-tag P&L intentionally does not sum to the portfolio total.
 * Positions with no tags form a trailing "Untagged" group (tag == null). Tagged groups are ordered
 * by net P&L descending; the untagged group, if present, is always last.
 */
fun tagStats(positions: List<ClosedPosition>): List<TagStat> {
    val byTag = LinkedHashMap<Tag, MutableList<ClosedPosition>>()
    val untagged = mutableListOf<ClosedPosition>()
    for (p in positions) {
        if (p.tags.isEmpty()) untagged.add(p)
        else for (tag in p.tags) byTag.getOrPut(tag) { mutableListOf() }.add(p)
    }
    val tagged = byTag.entries
        .map { (tag, ps) -> TagStat(tag, computeMetrics(ps)) }
        .sortedByDescending { it.metrics.netPnl }
    return if (untagged.isEmpty()) tagged else tagged + TagStat(null, computeMetrics(untagged))
}
