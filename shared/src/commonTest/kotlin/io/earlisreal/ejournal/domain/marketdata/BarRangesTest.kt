package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarRangesTest {

    private val today = LocalDate.parse("2026-06-12")

    private fun position(
        symbol: String = "AAPL",
        entry: String,
        exit: String,
        market: Market = Market.US_STOCKS,
    ) = ClosedPosition(
        symbol = symbol,
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 100.0,
        averageExitPrice = 110.0,
        shares = 10.0,
        fees = 1.0,
        profitLoss = 99.0,
        market = market,
    )

    // --- requiredRanges ---

    @Test
    fun `day trade one-minute range has 1-day lead and 1-day trail`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-06-10T09:31", exit = "2026-06-10T10:15")),
            today,
        )
        assertTrue(ranges.any { it == BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-11")) })
    }

    @Test
    fun `day trade requires one-minute bars for the trade day`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-06-10T09:31", exit = "2026-06-10T10:15")),
            today,
        )
        assertTrue(ranges.any { it == BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-11")) })
    }

    @Test
    fun `day trade also requires full daily history with a 60-day tail buffer`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-06-10T09:31", exit = "2026-06-10T10:15")),
            today,
        )
        // exit + 60 days = 2026-08-09, capped at today.
        assertTrue(ranges.any { it == BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("1970-01-01"), LocalDate.parse("2026-06-12")) })
    }

    @Test
    fun `swing trade requires full daily history with a 60-day tail buffer`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-01-15T09:31", exit = "2026-02-10T10:15")),
            today,
        )
        // exit + 60 days = 2026-04-11 (well before today, so uncapped).
        assertEquals(
            listOf(BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("1970-01-01"), LocalDate.parse("2026-04-11"))),
            ranges,
        )
    }

    @Test
    fun `swing tail buffer is capped at today`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-06-01T09:31", exit = "2026-06-10T10:15")),
            today,
        )
        assertEquals(today, ranges.single().to)
    }

    @Test
    fun `overlapping ranges for the same symbol and timeframe are merged`() {
        val ranges = requiredRanges(
            listOf(
                position(entry = "2026-01-15T09:31", exit = "2026-02-10T10:15"),
                position(entry = "2026-02-01T09:31", exit = "2026-03-01T10:15"),
            ),
            today,
        )
        assertEquals(1, ranges.size)
        assertEquals(LocalDate.parse("1970-01-01"), ranges.single().from)
        // Later trade dominates the tail: 2026-03-01 exit + 60 days = 2026-04-30.
        assertEquals(LocalDate.parse("2026-04-30"), ranges.single().to)
    }

    @Test
    fun `ranges for different symbols are not merged`() {
        val ranges = requiredRanges(
            listOf(
                position(symbol = "AAPL", entry = "2026-06-10T09:31", exit = "2026-06-10T10:15"),
                position(symbol = "TSLA", entry = "2026-06-10T09:31", exit = "2026-06-10T10:15"),
            ),
            today,
        )
        assertTrue(ranges.none { it.symbol == "AAPL" && it.symbol == "TSLA" })
        assertEquals(2, ranges.count { it.symbol == "AAPL" })
        assertEquals(2, ranges.count { it.symbol == "TSLA" })
    }

    @Test
    fun `adjacent day-trade days merge into one one-minute range`() {
        val ranges = requiredRanges(
            listOf(
                position(entry = "2026-06-09T09:31", exit = "2026-06-09T10:15"),
                position(entry = "2026-06-10T09:31", exit = "2026-06-10T10:15"),
            ),
            today,
        )
        // Trade 1: 2026-06-08..2026-06-10, Trade 2: 2026-06-09..2026-06-11 → merged 2026-06-08..2026-06-11
        assertTrue(ranges.any { it == BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")) })
    }

    @Test
    fun `day trade one-minute range spans the adjacent trading days across a weekend`() {
        // 2026-01-05 is a Monday: the previous trading day is Fri 2026-01-02 (skipping the weekend),
        // the next is Tue 2026-01-06 — so a Monday trade still gets a real prior session.
        val ranges = requiredRanges(
            listOf(position(entry = "2026-01-05T09:31", exit = "2026-01-05T10:15")),
            today,
        )
        assertTrue(ranges.any { it == BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-06")) })
    }

    // --- subtractCoverage ---

    private val junRange = BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))

    @Test
    fun `no coverage means the whole range is missing`() {
        assertEquals(listOf(junRange), subtractCoverage(junRange, null))
    }

    @Test
    fun `full coverage means nothing is missing`() {
        val coverage = BarCoverage(LocalDateTime.parse("2026-05-01T00:00"), LocalDateTime.parse("2026-06-15T00:00"))
        assertTrue(subtractCoverage(junRange, coverage).isEmpty())
    }

    @Test
    fun `daily partial coverage only extends the trailing tail, never the leading edge`() {
        // Daily is always requested from the full-history sentinel, so existing coverage already
        // reaches the symbol's first bar — re-fetching the leading edge would be a wasted probe.
        val coverage = BarCoverage(LocalDateTime.parse("2026-06-03T00:00"), LocalDateTime.parse("2026-06-07T00:00"))
        val missing = subtractCoverage(junRange, coverage)
        assertEquals(
            listOf(
                BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-10")),
            ),
            missing,
        )
    }

    @Test
    fun `one-minute partial coverage backfills both leading and trailing edges`() {
        val minRange = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        val coverage = BarCoverage(LocalDateTime.parse("2026-06-03T00:00"), LocalDateTime.parse("2026-06-07T00:00"))
        val missing = subtractCoverage(minRange, coverage)
        assertEquals(
            listOf(
                BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-02")),
                BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-10")),
            ),
            missing,
        )
    }

    // --- route ---

    @Test
    fun `daily ranges always route to yahoo`() {
        val routed = route(junRange, hasAlpacaKeys = false)
        assertEquals(listOf(RoutedRange(junRange, BarSource.YAHOO)), routed)
    }

    @Test
    fun `one-minute range routes to alpaca when keys exist regardless of age`() {
        val recent = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        val old    = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
        assertEquals(listOf(RoutedRange(recent, BarSource.ALPACA)), route(recent, hasAlpacaKeys = true))
        assertEquals(listOf(RoutedRange(old,    BarSource.ALPACA)), route(old,    hasAlpacaKeys = true))
    }

    @Test
    fun `one-minute range is unavailable without keys regardless of age`() {
        val recent = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        val old    = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
        assertEquals(listOf(RoutedRange(recent, BarSource.UNAVAILABLE)), route(recent, hasAlpacaKeys = false))
        assertEquals(listOf(RoutedRange(old,    BarSource.UNAVAILABLE)), route(old,    hasAlpacaKeys = false))
    }

    @Test
    fun `old one-minute range routes to alpaca when keys exist`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
        assertEquals(listOf(RoutedRange(range, BarSource.ALPACA)), route(range, hasAlpacaKeys = true))
    }

    @Test
    fun `old one-minute range is unavailable without keys`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
        assertEquals(listOf(RoutedRange(range, BarSource.UNAVAILABLE)), route(range, hasAlpacaKeys = false))
    }

    @Test
    fun `one-minute range spanning any date range routes entirely to alpaca`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-05-01"), LocalDate.parse("2026-06-10"))
        assertEquals(listOf(RoutedRange(range, BarSource.ALPACA)), route(range, hasAlpacaKeys = true))
    }

    // --- crypto ---

    @Test
    fun `crypto positions produce ranges tagged with the crypto market`() {
        val ranges = requiredRanges(
            listOf(position(symbol = "BTC", entry = "2026-06-10T09:31", exit = "2026-06-10T10:15", market = Market.CRYPTO)),
            today,
        )
        assertTrue(ranges.isNotEmpty())
        assertTrue(ranges.all { it.market == Market.CRYPTO })
    }

    @Test
    fun `crypto daily routes to yahoo crypto regardless of keys (deep history, keyless)`() {
        // Alpaca's crypto history is shallow (~2021, many coins absent); Yahoo has deep daily history.
        val range = BarRange("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"), Market.CRYPTO)
        assertEquals(listOf(RoutedRange(range, BarSource.YAHOO_CRYPTO)), route(range, hasAlpacaKeys = true))
        assertEquals(listOf(RoutedRange(range, BarSource.YAHOO_CRYPTO)), route(range, hasAlpacaKeys = false))
    }

    @Test
    fun `crypto one-minute routes to the alpaca crypto provider when keys exist`() {
        val range = BarRange("BTC", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"), Market.CRYPTO)
        assertEquals(listOf(RoutedRange(range, BarSource.ALPACA_CRYPTO)), route(range, hasAlpacaKeys = true))
    }

    @Test
    fun `crypto one-minute is unavailable without keys, but daily still routes to yahoo`() {
        val min = BarRange("BTC", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"), Market.CRYPTO)
        val daily = BarRange("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"), Market.CRYPTO)
        assertEquals(listOf(RoutedRange(min, BarSource.UNAVAILABLE)), route(min, hasAlpacaKeys = false))
        assertEquals(listOf(RoutedRange(daily, BarSource.YAHOO_CRYPTO)), route(daily, hasAlpacaKeys = false))
    }
}
