package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.domain.analytics.DaySummary
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun latestTradeMonth(summaries: Map<LocalDate, DaySummary>): Pair<Int, Int>? {
    val latest = summaries.keys.maxOrNull() ?: return null
    return latest.year to latest.monthNumber
}

class CalendarLatestMonthTest {

    @Test
    fun `returns null when summaries is empty`() {
        assertNull(latestTradeMonth(emptyMap()))
    }

    @Test
    fun `returns year and month of the latest date`() {
        val summaries = mapOf(
            LocalDate(2024, 1, 15) to DaySummary(LocalDate(2024, 1, 15), 100.0, 1),
            LocalDate(2024, 3, 20) to DaySummary(LocalDate(2024, 3, 20), -50.0, 1),
            LocalDate(2024, 2, 10) to DaySummary(LocalDate(2024, 2, 10), 200.0, 1),
        )
        assertEquals(2024 to 3, latestTradeMonth(summaries))
    }

    @Test
    fun `returns the single entry when map has one item`() {
        val summaries = mapOf(
            LocalDate(2025, 11, 5) to DaySummary(LocalDate(2025, 11, 5), 300.0, 2),
        )
        assertEquals(2025 to 11, latestTradeMonth(summaries))
    }
}
