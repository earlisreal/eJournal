package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.domain.analytics.DaySummary
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun summaries(vararg dates: LocalDate): Map<LocalDate, DaySummary> =
    dates.associateWith { DaySummary(it, 0.0, 1) }

class CalendarNavigationBoundsTest {

    @Test
    fun `no trades disables both directions`() {
        val s = emptyMap<LocalDate, DaySummary>()
        assertFalse(canGoPreviousMonth(s, 2024, 3))
        assertFalse(canGoNextMonth(s, 2024, 3))
    }

    @Test
    fun `single trade month disables both directions on that month`() {
        val s = summaries(LocalDate(2024, 3, 15))
        assertFalse(canGoPreviousMonth(s, 2024, 3))
        assertFalse(canGoNextMonth(s, 2024, 3))
    }

    @Test
    fun `earliest month allows next but not previous`() {
        val s = summaries(LocalDate(2024, 1, 10), LocalDate(2024, 3, 20))
        assertFalse(canGoPreviousMonth(s, 2024, 1))
        assertTrue(canGoNextMonth(s, 2024, 1))
    }

    @Test
    fun `latest month allows previous but not next`() {
        val s = summaries(LocalDate(2024, 1, 10), LocalDate(2024, 3, 20))
        assertTrue(canGoPreviousMonth(s, 2024, 3))
        assertFalse(canGoNextMonth(s, 2024, 3))
    }

    @Test
    fun `middle month allows both directions`() {
        val s = summaries(LocalDate(2024, 1, 10), LocalDate(2024, 3, 20))
        assertTrue(canGoPreviousMonth(s, 2024, 2))
        assertTrue(canGoNextMonth(s, 2024, 2))
    }

    @Test
    fun `bounds span across year boundary`() {
        val s = summaries(LocalDate(2023, 11, 5), LocalDate(2024, 2, 25))
        assertFalse(canGoPreviousMonth(s, 2023, 11))
        assertTrue(canGoNextMonth(s, 2023, 11))
        assertTrue(canGoPreviousMonth(s, 2023, 12))
        assertTrue(canGoNextMonth(s, 2023, 12))
        assertTrue(canGoPreviousMonth(s, 2024, 2))
        assertFalse(canGoNextMonth(s, 2024, 2))
    }
}
