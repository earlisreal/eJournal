package io.github.earlisreal.wickplot

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartInitialViewTest {

    private fun dailyBars(startDate: LocalDate, count: Int): List<OhlcvCandle> =
        (0 until count).map { i ->
            val d = startDate.plus(DatePeriod(days = i))
            OhlcvCandle(LocalDateTime(d, LocalTime(0, 0)), 10.0, 11.0, 9.0, 10.5, 100)
        }

    private fun minuteBars(day: LocalDate, count: Int): List<OhlcvCandle> =
        (0 until count).map { i ->
            val t = LocalTime(9 + (30 + i) / 60, (30 + i) % 60)
            OhlcvCandle(LocalDateTime(day, t), 10.0, 11.0, 9.0, 10.5, 100)
        }

    @Test
    fun `calendar framing brackets the trade with lead and tail padding`() {
        val start = LocalDate(2025, 1, 1)
        val bars = dailyBars(start, 300)
        val entry = LocalDateTime(2025, 5, 1, 0, 0)
        val exit = LocalDateTime(2025, 5, 20, 0, 0)

        val w = ChartInitialView.forTrade(bars, entry, exit, TradeFramingMode.CALENDAR)
        val first = bars[w.startIndex].timestamp.date
        val last = bars[w.startIndex + w.visibleBars - 1].timestamp.date

        // Window opens no earlier than entry-90d and ends no later than exit+60d.
        assertTrue(first >= entry.date.minus(DatePeriod(days = 90)), "first $first")
        assertTrue(last <= exit.date.plus(DatePeriod(days = 60)), "last $last")
        // Both the entry and exit bars are inside the window.
        val entryIdx = bars.indexOfFirst { it.timestamp.date == entry.date }
        val exitIdx = bars.indexOfFirst { it.timestamp.date == exit.date }
        assertTrue(entryIdx in w.startIndex until w.startIndex + w.visibleBars)
        assertTrue(exitIdx in w.startIndex until w.startIndex + w.visibleBars)
    }

    @Test
    fun `intraday framing puts the entry inside the window with lead room`() {
        val day = LocalDate(2025, 6, 2)
        val bars = minuteBars(day, 200)
        val entry = bars[50].timestamp
        val exit = bars[90].timestamp

        val w = ChartInitialView.forTrade(bars, entry, exit, TradeFramingMode.INTRADAY)
        assertTrue(w.startIndex < 50, "entry should not be pinned to the left edge (start=${w.startIndex})")
        assertTrue(50 in w.startIndex until w.startIndex + w.visibleBars)
        assertTrue(90 in w.startIndex until w.startIndex + w.visibleBars)
    }

    @Test
    fun `empty bars yield an empty window`() {
        val w = ChartInitialView.forTrade(emptyList(), LocalDateTime(2025, 1, 1, 0, 0), LocalDateTime(2025, 1, 1, 0, 0), TradeFramingMode.CALENDAR)
        assertEquals(0, w.startIndex)
        assertEquals(0, w.visibleBars)
    }
}
