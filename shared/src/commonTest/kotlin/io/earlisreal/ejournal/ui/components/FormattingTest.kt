package io.earlisreal.ejournal.ui.components

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {

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
