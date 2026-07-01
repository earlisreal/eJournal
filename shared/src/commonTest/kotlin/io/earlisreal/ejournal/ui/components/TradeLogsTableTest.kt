package io.earlisreal.ejournal.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class TradeLogsTableTest {

    @Test
    fun subMinuteDayTradeShowsOnlySeconds() {
        assertEquals("34s", formatHeldDuration(34, isDay = true))
    }

    @Test
    fun instantDayTradeShowsZeroSeconds() {
        assertEquals("0s", formatHeldDuration(0, isDay = true))
    }

    @Test
    fun minutesAndSecondsDropTheZeroHour() {
        assertEquals("1m 5s", formatHeldDuration(65, isDay = true))
    }

    @Test
    fun wholeMinuteOmitsSeconds() {
        assertEquals("1m", formatHeldDuration(60, isDay = true))
    }

    @Test
    fun wholeHourOmitsMinutesAndSeconds() {
        assertEquals("1h", formatHeldDuration(3600, isDay = true))
    }

    @Test
    fun hourWithSecondsOmitsZeroMinutes() {
        assertEquals("1h 30s", formatHeldDuration(3630, isDay = true))
    }

    @Test
    fun hourAndMinutesOmitZeroSeconds() {
        assertEquals("1h 30m", formatHeldDuration(5400, isDay = true))
    }

    @Test
    fun allThreeUnitsShownWhenNonZero() {
        assertEquals("1h 1m 1s", formatHeldDuration(3661, isDay = true))
    }

    @Test
    fun swingTradeShowsDays() {
        assertEquals("2d", formatHeldDuration(172800, isDay = false))
    }
}
