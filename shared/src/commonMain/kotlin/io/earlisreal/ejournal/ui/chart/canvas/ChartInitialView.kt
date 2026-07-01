package io.earlisreal.ejournal.ui.chart.canvas

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Computes the initial visible [BarWindow] that frames a trade — the Canvas equivalent of the JCEF
 * chart's `initialViewCommand`. Daily/weekly load full history, so bracket the trade with
 * [LEAD_DAYS]/[TAIL_DAYS] of context; intraday data is already a tight per-trade window, so frame the
 * held span with the entry set in from the left edge.
 */
object ChartInitialView {
    private const val LEAD_DAYS = 90
    private const val TAIL_DAYS = 60

    fun forTrade(bars: List<Bar>, entry: LocalDateTime, exit: LocalDateTime, tf: ChartTimeframe): BarWindow {
        if (bars.isEmpty()) return BarWindow(0, 0)
        if (bars.size <= BarWindow.MIN_BARS) return BarWindow(0, bars.size)

        return when (tf) {
            ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> {
                val leadDate = entry.date.minus(DatePeriod(days = LEAD_DAYS))
                val tailDate = exit.date.plus(DatePeriod(days = TAIL_DAYS))
                val start = bars.indexOfFirst { it.timestamp.date >= leadDate }.let { if (it < 0) 0 else it }
                val endExclusive = (bars.indexOfLast { it.timestamp.date <= tailDate } + 1).let { if (it <= start) bars.size else it }
                windowOf(start, endExclusive - start, bars.size)
            }
            else -> {
                val entryIdx = bars.indexOfLast { it.timestamp <= entry }.coerceAtLeast(0)
                val exitIdx = bars.indexOfLast { it.timestamp <= exit }.coerceIn(entryIdx, bars.lastIndex)
                val span = exitIdx - entryIdx + 1
                val visible = maxOf(60, span * 2)
                val start = entryIdx - (visible * 0.25).toInt()
                windowOf(start, visible, bars.size)
            }
        }
    }

    private fun windowOf(start: Int, visibleBars: Int, totalBars: Int): BarWindow {
        val visible = visibleBars.coerceIn(minOf(BarWindow.MIN_BARS, totalBars), totalBars)
        return BarWindow(start.coerceIn(0, maxOf(0, totalBars - visible)), visible)
    }
}
