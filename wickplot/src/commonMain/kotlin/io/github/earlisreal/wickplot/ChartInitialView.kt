package io.github.earlisreal.wickplot

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * How to frame a trade in the initial view.
 * - [CALENDAR]: the series spans a long history (e.g. daily/weekly bars), so bracket the trade with
 *   a fixed number of calendar days of lead/tail context.
 * - [INTRADAY]: the series is already a tight per-trade window (e.g. one-minute bars), so frame the
 *   held span by bar count with the entry set in from the left edge.
 */
enum class TradeFramingMode { CALENDAR, INTRADAY }

/** Computes the initial visible [BarWindow] that frames a trade between [entry] and [exit]. */
object ChartInitialView {
    const val DEFAULT_LEAD_DAYS = 90
    const val DEFAULT_TAIL_DAYS = 60

    fun forTrade(
        bars: List<Candle>,
        entry: LocalDateTime,
        exit: LocalDateTime,
        mode: TradeFramingMode,
        leadDays: Int = DEFAULT_LEAD_DAYS,
        tailDays: Int = DEFAULT_TAIL_DAYS,
    ): BarWindow {
        if (bars.isEmpty()) return BarWindow(0, 0)
        if (bars.size <= BarWindow.MIN_BARS) return BarWindow(0, bars.size)

        return when (mode) {
            TradeFramingMode.CALENDAR -> {
                val leadDate = entry.date.minus(DatePeriod(days = leadDays))
                val tailDate = exit.date.plus(DatePeriod(days = tailDays))
                val start = bars.indexOfFirst { it.timestamp.date >= leadDate }.let { if (it < 0) 0 else it }
                val endExclusive = (bars.indexOfLast { it.timestamp.date <= tailDate } + 1).let { if (it <= start) bars.size else it }
                windowOf(start, endExclusive - start, bars.size)
            }
            TradeFramingMode.INTRADAY -> {
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
