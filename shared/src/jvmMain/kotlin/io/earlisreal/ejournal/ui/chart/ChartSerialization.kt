package io.earlisreal.ejournal.ui.chart

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.marketdata.VwapPoint
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

object ChartSerialization {
    private const val VIEW_LEAD_DAYS = 90
    private const val VIEW_TAIL_DAYS = 60

    // Always emit numeric UTC epoch seconds so Lightweight Charts never has to switch between
    // UTCTimestamp and BusinessDay time types (which throws at runtime).
    // For daily/weekly bars, normalise to midnight UTC of that date so day-granularity
    // comparisons work correctly regardless of whether the raw bar is at market-open.
    fun barTimeSec(ts: LocalDateTime, tf: ChartTimeframe): Long = when (tf) {
        ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> ts.date.atStartOfDayIn(TimeZone.UTC).epochSeconds
        else -> ts.toInstant(TimeZone.UTC).epochSeconds
    }

    fun candlesJson(bars: List<Bar>, tf: ChartTimeframe): String =
        bars.joinToString(",", "[", "]") { b ->
            """{"time":${barTimeSec(b.timestamp, tf)},"open":${b.open},"high":${b.high},"low":${b.low},"close":${b.close}}"""
        }

    fun volumeJson(bars: List<Bar>, tf: ChartTimeframe): String =
        bars.joinToString(",", "[", "]") { b ->
            val color = if (b.close >= b.open) "rgba(38,166,154,0.5)" else "rgba(239,83,80,0.5)"
            """{"time":${barTimeSec(b.timestamp, tf)},"value":${b.volume},"color":"$color"}"""
        }

    fun vwapJson(vwap: List<VwapPoint>, tf: ChartTimeframe): String =
        vwap.joinToString(",", "[", "]") { v ->
            """{"time":${barTimeSec(v.timestamp, tf)},"value":${"%.4f".format(v.value)}}"""
        }

    // v5: markers are drawn by the PriceDiamonds primitive at an exact price — emit {time, price, color}.
    // Deduplicate by transaction id so the same fill doesn't produce two overlapping diamonds.
    fun markersJson(transactions: List<Transaction>, tf: ChartTimeframe): String {
        val seen = mutableSetOf<Long>()
        return transactions.filter { seen.add(it.id) }.joinToString(",", "[", "]") { tx ->
            val color = if (tx.action == Action.BUY) "rgba(165,214,167,0.8)" else "rgba(244,143,177,0.8)"
            """{"time":${barTimeSec(snapToBucket(tx.datetime, tf), tf)},"price":${tx.price},"color":"$color"}"""
        }
    }

    // Bar index of the first trade (entry), snapped to the active timeframe so it lines up with the
    // entry marker. The JS side scrolls so this bar sits a few bars in from the left edge.
    fun firstTradeBarIndex(entry: LocalDateTime, bars: List<Bar>, tf: ChartTimeframe): Int {
        val idx = when (tf) {
            ChartTimeframe.DAILY, ChartTimeframe.WEEKLY ->
                bars.indexOfFirst { it.timestamp.date >= entry.date }
            else -> {
                val snapped = snapToBucket(entry, tf)
                bars.indexOfFirst { it.timestamp >= snapped }
            }
        }
        return if (idx < 0) 0 else idx
    }

    // Daily/weekly charts load the symbol's full history, so frame the trade with an explicit
    // visible range [entry-90d, exit+60d] (clamped to the loaded data) and let the user zoom out
    // from there. Intraday data is already a tight per-trade window, so just scroll the entry into
    // view at the current zoom.
    fun initialViewCommand(entry: LocalDateTime, exit: LocalDateTime, bars: List<Bar>, tf: ChartTimeframe): String =
        when (tf) {
            ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> {
                val firstSec = bars.firstOrNull()?.let { barTimeSec(it.timestamp, tf) }
                val lastSec = bars.lastOrNull()?.let { barTimeSec(it.timestamp, tf) }
                if (firstSec == null || lastSec == null) {
                    "scrollToFirstTrade(0)"
                } else {
                    val from = maxOf(dateEpochSec(entry.date.minus(DatePeriod(days = VIEW_LEAD_DAYS))), firstSec)
                    val to = minOf(dateEpochSec(exit.date.plus(DatePeriod(days = VIEW_TAIL_DAYS))), lastSec)
                    if (from >= to) "scrollToFirstTrade(${firstTradeBarIndex(entry, bars, tf)})"
                    else "setVisibleRange($from, $to)"
                }
            }
            else -> "scrollToFirstTrade(${firstTradeBarIndex(entry, bars, tf)})"
        }

    private fun dateEpochSec(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).epochSeconds

    // Snap a transaction time down to the start of its timeframe bucket so it lines up with a bar.
    private fun snapToBucket(dt: LocalDateTime, tf: ChartTimeframe): LocalDateTime = when (tf) {
        ChartTimeframe.ONE_MIN -> LocalDateTime(dt.date, LocalTime(dt.hour, dt.minute))
        ChartTimeframe.FIVE_MIN -> {
            val m = dt.minute - (dt.minute % 5)
            LocalDateTime(dt.date, LocalTime(dt.hour, m))
        }
        ChartTimeframe.FIFTEEN_MIN -> {
            val total = dt.hour * 60 + dt.minute
            val bucket = total - (total % 15)
            LocalDateTime(dt.date, LocalTime(bucket / 60, bucket % 60))
        }
        ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> dt
    }
}
