package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.domain.model.ClosedPosition
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
    ) = ClosedPosition(
        symbol = symbol,
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 100.0,
        averageExitPrice = 110.0,
        shares = 10.0,
        fees = 1.0,
        profitLoss = 99.0,
    )

    // --- requiredRanges ---

    @Test
    fun `day trade requires one-minute bars for the trade day`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-06-10T09:31", exit = "2026-06-10T10:15")),
            today,
        )
        assertEquals(
            listOf(BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))),
            ranges,
        )
    }

    @Test
    fun `swing trade requires daily bars with 60-day lead and 30-day tail buffer`() {
        val ranges = requiredRanges(
            listOf(position(entry = "2026-01-15T09:31", exit = "2026-02-10T10:15")),
            today,
        )
        assertEquals(
            listOf(BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("2025-11-16"), LocalDate.parse("2026-03-12"))),
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
        assertEquals(LocalDate.parse("2025-11-16"), ranges.single().from)
        assertEquals(LocalDate.parse("2026-03-31"), ranges.single().to)
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
        assertEquals(2, ranges.size)
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
        assertEquals(
            listOf(BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-10"))),
            ranges,
        )
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
    fun `partial coverage leaves missing edges`() {
        val coverage = BarCoverage(LocalDateTime.parse("2026-06-03T00:00"), LocalDateTime.parse("2026-06-07T00:00"))
        val missing = subtractCoverage(junRange, coverage)
        assertEquals(
            listOf(
                BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-02")),
                BarRange("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-10")),
            ),
            missing,
        )
    }

    // --- route ---

    @Test
    fun `daily ranges always route to yahoo`() {
        val routed = route(junRange, today, hasAlpacaKeys = false)
        assertEquals(listOf(RoutedRange(junRange, BarSource.YAHOO)), routed)
    }

    @Test
    fun `recent one-minute range routes to yahoo`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(listOf(RoutedRange(range, BarSource.YAHOO)), route(range, today, hasAlpacaKeys = false))
    }

    @Test
    fun `old one-minute range routes to alpaca when keys exist`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
        assertEquals(listOf(RoutedRange(range, BarSource.ALPACA)), route(range, today, hasAlpacaKeys = true))
    }

    @Test
    fun `old one-minute range is unavailable without keys`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
        assertEquals(listOf(RoutedRange(range, BarSource.UNAVAILABLE)), route(range, today, hasAlpacaKeys = false))
    }

    @Test
    fun `one-minute range spanning the 30-day boundary splits between providers`() {
        val range = BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-05-01"), LocalDate.parse("2026-06-10"))
        val routed = route(range, today, hasAlpacaKeys = true)
        // Yahoo window: today - 29 days = 2026-05-14 onwards
        assertEquals(
            listOf(
                RoutedRange(BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-13")), BarSource.ALPACA),
                RoutedRange(BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-05-14"), LocalDate.parse("2026-06-10")), BarSource.YAHOO),
            ),
            routed,
        )
    }
}
