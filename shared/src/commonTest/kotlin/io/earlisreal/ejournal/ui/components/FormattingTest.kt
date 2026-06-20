package io.earlisreal.ejournal.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {

    @Test
    fun shortDateAbbreviatesMonth() {
        assertEquals("Jun 18", shortDate(LocalDate.parse("2026-06-18")))
        assertEquals("Jan 1", shortDate(LocalDate.parse("2026-01-01")))
    }

    @Test
    fun formatDurationAdaptsToMagnitudeWithSecondsUnderADay() {
        assertEquals("2d 4h", formatDuration(2 * 86400.0 + 4 * 3600.0))
        assertEquals("3h 12m 0s", formatDuration(3 * 3600.0 + 12 * 60.0))
        assertEquals("2h 15m 30s", formatDuration(2 * 3600.0 + 15 * 60.0 + 30.0))
        assertEquals("45m 0s", formatDuration(45 * 60.0))
        assertEquals("30s", formatDuration(30.0))
    }

    @Test
    fun compactMoneyAbbreviatesLargeValues() {
        assertEquals("$0", compactMoney(0.0))
        assertEquals("$950", compactMoney(950.0))
        assertEquals("$3.4k", compactMoney(3400.0))
        assertEquals("−$640", compactMoney(-640.0))
        assertEquals("$2.5M", compactMoney(2_500_000.0))
    }

    @Test
    fun dayTradeHoldShowsHoursMinutesSeconds() {
        val entry = LocalDateTime.parse("2024-01-01T09:30:00")
        val exit = LocalDateTime.parse("2024-01-01T11:45:30")
        assertEquals("2h 15m 30s", formatHold(entry, exit, isDay = true))
    }

    @Test
    fun dayTradeHoldKeepsZeroSecondsComponent() {
        val entry = LocalDateTime.parse("2024-01-01T09:30:00")
        val exit = LocalDateTime.parse("2024-01-01T09:45:00")
        assertEquals("0h 15m 0s", formatHold(entry, exit, isDay = true))
    }

    @Test
    fun swingTradeHoldShowsWholeDaysOnly() {
        val entry = LocalDateTime.parse("2024-01-01T09:30:00")
        val exit = LocalDateTime.parse("2024-01-04T15:00:00")
        assertEquals("3d", formatHold(entry, exit, isDay = false))
    }
}
