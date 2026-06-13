package io.earlisreal.ejournal.domain.marketdata

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarAggregatorTest {

    private fun bar(
        symbol: String = "AAPL",
        timeframe: Timeframe = Timeframe.ONE_MINUTE,
        timestamp: String,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Long,
    ) = Bar(symbol, timeframe, LocalDateTime.parse(timestamp), open, high, low, close, volume)

    @Test
    fun oneMinPassthrough() {
        val bars = listOf(
            bar(timestamp = "2024-01-02T09:30", open = 10.0, high = 11.0, low = 9.5,  close = 10.5, volume = 1000),
            bar(timestamp = "2024-01-02T09:31", open = 10.5, high = 12.0, low = 10.0, close = 11.0, volume = 2000),
        )
        val result = BarAggregator.aggregate(bars, ChartTimeframe.ONE_MIN)
        assertEquals(2, result.bars.size)
        assertEquals(2, result.vwap.size)
    }

    @Test
    fun fiveMinAggregatesOhlcv() {
        val bars = (0 until 5).map { i ->
            bar(timestamp = "2024-01-02T09:3$i", open = 10.0 + i, high = 11.0 + i, low = 9.0 + i, close = 10.5 + i, volume = 1000L)
        }
        val result = BarAggregator.aggregate(bars, ChartTimeframe.FIVE_MIN)
        assertEquals(1, result.bars.size)
        val agg = result.bars[0]
        assertEquals(10.0,  agg.open)
        assertEquals(15.0,  agg.high)
        assertEquals(9.0,   agg.low)
        assertEquals(14.5,  agg.close)
        assertEquals(5000L, agg.volume)
        assertEquals(1, result.vwap.size)
    }

    @Test
    fun vwapResetsAtDayBoundary() {
        val bars = listOf(
            bar(timestamp = "2024-01-02T09:30", open = 10.0, high = 12.0, low = 10.0, close = 11.0, volume = 1000),
            bar(timestamp = "2024-01-03T09:30", open = 20.0, high = 22.0, low = 20.0, close = 21.0, volume = 1000),
        )
        val result = BarAggregator.aggregate(bars, ChartTimeframe.ONE_MIN)
        assertEquals(2, result.vwap.size)
        // Day 1: typical = (12+10+11)/3 = 11.0
        assertEquals(11.0, result.vwap[0].value, 0.001)
        // Day 2 resets: typical = (22+20+21)/3 = 21.0
        assertEquals(21.0, result.vwap[1].value, 0.001)
    }

    @Test
    fun weeklyAggregatesMonToFri() {
        val bars = listOf(
            bar(timeframe = Timeframe.DAILY, timestamp = "2024-01-01T00:00", open = 10.0, high = 11.0, low = 9.0,  close = 10.5, volume = 1000),
            bar(timeframe = Timeframe.DAILY, timestamp = "2024-01-02T00:00", open = 10.5, high = 12.0, low = 10.0, close = 11.5, volume = 2000),
            bar(timeframe = Timeframe.DAILY, timestamp = "2024-01-03T00:00", open = 11.5, high = 13.0, low = 11.0, close = 12.0, volume = 1500),
        )
        val result = BarAggregator.aggregate(bars, ChartTimeframe.WEEKLY)
        assertEquals(1, result.bars.size)
        val w = result.bars[0]
        assertEquals(10.0, w.open)
        assertEquals(13.0, w.high)
        assertEquals(9.0,  w.low)
        assertEquals(12.0, w.close)
        assertEquals(4500L, w.volume)
        assertTrue(result.vwap.isEmpty())
    }

    @Test
    fun dailyPassthroughNoVwap() {
        val bars = listOf(
            bar(timeframe = Timeframe.DAILY, timestamp = "2024-01-02T00:00", open = 10.0, high = 11.0, low = 9.0, close = 10.5, volume = 1000),
        )
        val result = BarAggregator.aggregate(bars, ChartTimeframe.DAILY)
        assertEquals(1, result.bars.size)
        assertTrue(result.vwap.isEmpty())
    }
}
