package io.earlisreal.ejournal.ui.shell

import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.model.Portfolio

/** The resolved filter handed to screens: a concrete date range, the segment, and the portfolio. */
data class FilterState(
    val portfolio: Portfolio?,
    val dateRange: DateRange,
    val segment: Segment,
)
