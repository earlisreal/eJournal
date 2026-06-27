package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** An inclusive day range of bars needed for one symbol+timeframe in a given market. */
data class BarRange(
    val symbol: String,
    val timeframe: Timeframe,
    val from: LocalDate,
    val to: LocalDate,
    val market: Market = Market.US_STOCKS,
)

enum class BarSource { YAHOO, YAHOO_CRYPTO, ALPACA, ALPACA_CRYPTO, UNAVAILABLE }

data class RoutedRange(val range: BarRange, val source: BarSource)

/**
 * Daily bars are stored as full history: the lead is a fixed sentinel that Yahoo clips to the
 * symbol's first available trading day, and the tail runs to exit + [DAILY_TAIL_DAYS] so storage
 * fully backs the chart's initial view window. The chart loads all of it and just zooms to the
 * trade. (Pre-1970 history is dropped — irrelevant for a trading journal.)
 */
private val EARLIEST_DAILY = LocalDate.parse("1970-01-01")
private const val DAILY_TAIL_DAYS = 60

/**
 * 1-min bars cover the trade day plus the immediately adjacent trading sessions, so the previous
 * day's and next day's intraday action is visible. Weekends are skipped (a Monday trade reaches
 * back to Friday); market holidays are not modelled, so a holiday-adjacent session may be empty.
 */
fun previousTradingDay(date: LocalDate): LocalDate {
    var d = date.minus(DatePeriod(days = 1))
    while (d.dayOfWeek.ordinal >= 5) d = d.minus(DatePeriod(days = 1)) // Sat=5, Sun=6
    return d
}

fun nextTradingDay(date: LocalDate): LocalDate {
    var d = date.plus(DatePeriod(days = 1))
    while (d.dayOfWeek.ordinal >= 5) d = d.plus(DatePeriod(days = 1)) // Sat=5, Sun=6
    return d
}

/**
 * Bars needed to chart the given positions: 1-min bars for day trades, buffered daily
 * bars for swings. Overlapping or adjacent ranges per symbol+timeframe are merged.
 */
fun requiredRanges(positions: List<ClosedPosition>, today: LocalDate): List<BarRange> {
    val raw = positions.flatMap { position ->
        val dailyRange = BarRange(
            symbol = position.symbol,
            timeframe = Timeframe.DAILY,
            from = EARLIEST_DAILY,
            to = minOf(position.exitDatetime.date.plus(DatePeriod(days = DAILY_TAIL_DAYS)), today),
            market = position.market,
        )
        when (classifyTradeType(position)) {
            TradeType.DAY -> listOf(
                BarRange(
                    symbol = position.symbol,
                    timeframe = Timeframe.ONE_MINUTE,
                    from = previousTradingDay(position.entryDatetime.date),
                    to = minOf(nextTradingDay(position.exitDatetime.date), today),
                    market = position.market,
                ),
                dailyRange,
            )
            TradeType.SWING -> listOf(dailyRange)
        }
    }
    return raw
        .groupBy { Triple(it.symbol, it.timeframe, it.market) }
        .flatMap { (_, ranges) -> mergeRanges(ranges) }
}

private fun mergeRanges(ranges: List<BarRange>): List<BarRange> {
    val sorted = ranges.sortedBy { it.from }
    val merged = mutableListOf<BarRange>()
    for (range in sorted) {
        val last = merged.lastOrNull()
        if (last != null && range.from <= last.to.plus(DatePeriod(days = 1))) {
            merged[merged.lastIndex] = last.copy(to = maxOf(last.to, range.to))
        } else {
            merged.add(range)
        }
    }
    return merged
}

/**
 * The parts of [range] not already in storage. Coverage is coarse (min/max), so only
 * the missing leading/trailing edges are returned — over-fetch is harmless (upserts).
 *
 * Daily is the exception: it is always requested from [EARLIEST_DAILY], so any existing daily
 * coverage already reaches the symbol's first available bar. Re-fetching the leading edge would
 * just fire an empty pre-history request every sync (and keep the symbol perpetually "in work"),
 * so for daily we only ever extend the trailing tail once coverage exists. A fresh symbol (null
 * coverage) still pulls the full history. 1-min keeps both edges — it is fetched in per-trade
 * windows, so an earlier trade can still need a leading backfill.
 */
fun subtractCoverage(range: BarRange, coverage: BarCoverage?): List<BarRange> {
    if (coverage == null) return listOf(range)
    val missing = mutableListOf<BarRange>()
    val coveredFrom = coverage.first.date
    val coveredTo = coverage.last.date
    val backfillLeadingEdge = range.timeframe != Timeframe.DAILY
    if (backfillLeadingEdge && range.from < coveredFrom) {
        missing.add(range.copy(to = minOf(range.to, coveredFrom.minus(DatePeriod(days = 1)))))
    }
    if (range.to > coveredTo) {
        missing.add(range.copy(from = maxOf(range.from, coveredTo.plus(DatePeriod(days = 1)))))
    }
    return missing
}

/**
 * Picks the provider per range.
 *
 * Symmetric by timeframe: DAILY → Yahoo, 1-min → Alpaca; the crypto variants differ only in symbol
 * format. Crypto daily uses Yahoo because Alpaca's crypto history is shallow (~2021 onward, and many
 * altcoins are absent entirely), so older crypto trades have no daily data there — Yahoo has deep
 * crypto history and is keyless. Crypto 1-min still needs Alpaca (Yahoo has no deep intraday).
 *
 * NOTE: daily-is-Yahoo-only is load-bearing. Yahoo (and the YahooCrypto wrapper around it) normalize
 * daily bar timestamps to the date so a calendar day maps to exactly one OhlcvBar row (deduped by the
 * primary key across re-syncs). If you ever route DAILY to Alpaca, its 1Day bars need the same
 * date-normalization — otherwise the same day stored under two intraday timestamps becomes two rows
 * again (see the duplicate-daily-bar bug this guards against).
 */
fun route(range: BarRange, hasAlpacaKeys: Boolean): List<RoutedRange> {
    if (range.market == Market.CRYPTO) {
        if (range.timeframe == Timeframe.DAILY) return listOf(RoutedRange(range, BarSource.YAHOO_CRYPTO))
        val source = if (hasAlpacaKeys) BarSource.ALPACA_CRYPTO else BarSource.UNAVAILABLE
        return listOf(RoutedRange(range, source))
    }
    if (range.timeframe == Timeframe.DAILY) return listOf(RoutedRange(range, BarSource.YAHOO))
    val source = if (hasAlpacaKeys) BarSource.ALPACA else BarSource.UNAVAILABLE
    return listOf(RoutedRange(range, source))
}
