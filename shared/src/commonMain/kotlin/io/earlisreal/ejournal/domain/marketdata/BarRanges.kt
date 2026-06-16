package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** An inclusive day range of bars needed for one symbol+timeframe. */
data class BarRange(val symbol: String, val timeframe: Timeframe, val from: LocalDate, val to: LocalDate)

enum class BarSource { YAHOO, ALPACA, UNAVAILABLE }

data class RoutedRange(val range: BarRange, val source: BarSource)

/** Daily bars around a swing trade: lead for chart context, tail to see the aftermath. */
private const val SWING_LEAD_DAYS = 60
private const val SWING_TAIL_DAYS = 30

/** 1-min bars: one extra day before entry and after exit for context around the trade. */
private const val INTRADAY_LEAD_DAYS = 1
private const val INTRADAY_TAIL_DAYS = 1

/**
 * Bars needed to chart the given positions: 1-min bars for day trades, buffered daily
 * bars for swings. Overlapping or adjacent ranges per symbol+timeframe are merged.
 */
fun requiredRanges(positions: List<ClosedPosition>, today: LocalDate): List<BarRange> {
    val raw = positions.flatMap { position ->
        val dailyRange = BarRange(
            symbol = position.symbol,
            timeframe = Timeframe.DAILY,
            from = position.entryDatetime.date.minus(DatePeriod(days = SWING_LEAD_DAYS)),
            to = minOf(position.exitDatetime.date.plus(DatePeriod(days = SWING_TAIL_DAYS)), today),
        )
        when (classifyTradeType(position)) {
            TradeType.DAY -> listOf(
                BarRange(
                    symbol = position.symbol,
                    timeframe = Timeframe.ONE_MINUTE,
                    from = position.entryDatetime.date.minus(DatePeriod(days = INTRADAY_LEAD_DAYS)),
                    to = minOf(position.exitDatetime.date.plus(DatePeriod(days = INTRADAY_TAIL_DAYS)), today),
                ),
                dailyRange,
            )
            TradeType.SWING -> listOf(dailyRange)
        }
    }
    return raw
        .groupBy { it.symbol to it.timeframe }
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
 */
fun subtractCoverage(range: BarRange, coverage: BarCoverage?): List<BarRange> {
    if (coverage == null) return listOf(range)
    val missing = mutableListOf<BarRange>()
    val coveredFrom = coverage.first.date
    val coveredTo = coverage.last.date
    if (range.from < coveredFrom) {
        missing.add(range.copy(to = minOf(range.to, coveredFrom.minus(DatePeriod(days = 1)))))
    }
    if (range.to > coveredTo) {
        missing.add(range.copy(from = maxOf(range.from, coveredTo.plus(DatePeriod(days = 1)))))
    }
    return missing
}

/**
 * Picks the provider per range: daily always Yahoo; 1-min always Alpaca (keys required
 * for full extended-hours coverage — Yahoo only serves regular-hours bars).
 */
fun route(range: BarRange, hasAlpacaKeys: Boolean): List<RoutedRange> {
    if (range.timeframe == Timeframe.DAILY) return listOf(RoutedRange(range, BarSource.YAHOO))
    val source = if (hasAlpacaKeys) BarSource.ALPACA else BarSource.UNAVAILABLE
    return listOf(RoutedRange(range, source))
}
