package io.earlisreal.ejournal.ui.shell

import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.model.Portfolio

/** The resolved filter handed to screens: portfolio, date range, segment, and the tag filter. */
data class FilterState(
    val portfolio: Portfolio?,
    val dateRange: DateRange,
    val segment: Segment,
    val selectedTagIds: Set<Long> = emptySet(),
    val tagMatch: TagMatch = TagMatch.ANY,
)
