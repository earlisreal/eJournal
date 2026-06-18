package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.data.repository.BarCoverage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun hasIntradayDataForDate(coverage: BarCoverage?, tradeDate: LocalDate): Boolean {
    coverage ?: return false
    return tradeDate >= coverage.first.date && tradeDate <= coverage.last.date
}

class IntradayAvailabilityTest {
    private fun dt(year: Int, month: Int, day: Int) =
        LocalDateTime(LocalDate(year, month, day), LocalTime(0, 0))

    @Test
    fun `returns false when coverage is null`() {
        assertFalse(hasIntradayDataForDate(null, LocalDate(2024, 1, 15)))
    }

    @Test
    fun `returns true when trade date is within coverage`() {
        val coverage = BarCoverage(dt(2024, 1, 1), dt(2024, 3, 31))
        assertTrue(hasIntradayDataForDate(coverage, LocalDate(2024, 1, 15)))
    }

    @Test
    fun `returns false when trade date is before coverage`() {
        val coverage = BarCoverage(dt(2024, 2, 1), dt(2024, 3, 31))
        assertFalse(hasIntradayDataForDate(coverage, LocalDate(2024, 1, 15)))
    }

    @Test
    fun `returns false when trade date is after coverage`() {
        val coverage = BarCoverage(dt(2024, 1, 1), dt(2024, 1, 31))
        assertFalse(hasIntradayDataForDate(coverage, LocalDate(2024, 3, 15)))
    }

    @Test
    fun `returns true on the exact first day of coverage`() {
        val coverage = BarCoverage(dt(2024, 1, 15), dt(2024, 3, 31))
        assertTrue(hasIntradayDataForDate(coverage, LocalDate(2024, 1, 15)))
    }

    @Test
    fun `returns true on the exact last day of coverage`() {
        val coverage = BarCoverage(dt(2024, 1, 1), dt(2024, 1, 15))
        assertTrue(hasIntradayDataForDate(coverage, LocalDate(2024, 1, 15)))
    }
}
