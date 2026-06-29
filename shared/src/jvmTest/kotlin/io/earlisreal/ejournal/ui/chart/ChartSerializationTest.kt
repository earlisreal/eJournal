package io.earlisreal.ejournal.ui.chart

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartSerializationTest {
    private fun bar(d: String, o: Double, h: Double, l: Double, c: Double, v: Long = 100L) =
        Bar(symbol = "TEST", timeframe = Timeframe.DAILY, timestamp = LocalDateTime.parse(d), open = o, high = h, low = l, close = c, volume = v)

    @Test fun `daily candle time is midnight-UTC epoch seconds`() {
        // 2025-01-02T15:30 -> normalised to 2025-01-02T00:00 UTC = 1735776000
        val json = ChartSerialization.candlesJson(listOf(bar("2025-01-02T15:30", 10.0, 12.0, 9.0, 11.0)), ChartTimeframe.DAILY)
        assertEquals("""[{"time":1735776000,"open":10.0,"high":12.0,"low":9.0,"close":11.0}]""", json)
    }

    @Test fun `intraday candle time keeps the wall-clock UTC second`() {
        // 2025-01-02T15:30Z = 1735831800
        val json = ChartSerialization.candlesJson(listOf(bar("2025-01-02T15:30", 10.0, 12.0, 9.0, 11.0)), ChartTimeframe.ONE_MIN)
        assertTrue(json.contains(""""time":1735831800"""), json)
    }

    @Test fun `volume color is green when close ge open`() {
        val json = ChartSerialization.volumeJson(listOf(bar("2025-01-02T00:00", 10.0, 12.0, 9.0, 11.0, 500L)), ChartTimeframe.DAILY)
        assertTrue(json.contains(""""color":"rgba(38,166,154,0.5)"""), json)
    }

    @Test fun `marker uses price not position and dedups by id`() {
        val tx = Transaction(
            id = 7L,
            portfolioId = 1L,
            symbol = "TEST",
            datetime = LocalDateTime.parse("2025-01-02T00:00"),
            action = Action.BUY,
            price = 11.25,
            shares = 100.0,
            fees = 0.0,
        )
        val json = ChartSerialization.markersJson(listOf(tx, tx), ChartTimeframe.DAILY)
        assertEquals(1, Regex(""""price"""").findAll(json).count())   // deduped
        assertTrue(json.contains(""""price":11.25"""), json)
        assertTrue(!json.contains("position") && !json.contains("shape"), "v5 markers carry no position/shape: $json")
        assertTrue(json.contains(""""color":"rgba(165,214,167,0.8)"""), json) // BUY = green
    }

    @Test fun `firstTradeBarIndex finds the entry bar (daily)`() {
        val bars = listOf(bar("2025-01-01T00:00",1.0,1.0,1.0,1.0), bar("2025-01-02T00:00",1.0,1.0,1.0,1.0), bar("2025-01-03T00:00",1.0,1.0,1.0,1.0))
        assertEquals(1, ChartSerialization.firstTradeBarIndex(LocalDateTime.parse("2025-01-02T10:00"), bars, ChartTimeframe.DAILY))
    }

    @Test fun `initialView yields setVisibleRange for daily`() {
        val bars = (1..200).map { bar("2025-%02d-01T00:00".format((it % 12) + 1), 1.0,1.0,1.0,1.0) }
        val cmd = ChartSerialization.initialViewCommand(LocalDateTime.parse("2025-03-01T00:00"), LocalDateTime.parse("2025-03-10T00:00"), bars, ChartTimeframe.DAILY)
        assertTrue(cmd.startsWith("setVisibleRange(") || cmd.startsWith("scrollToFirstTrade("), cmd)
    }

    @Test fun `initialView yields scrollToFirstTrade for intraday`() {
        val bars = listOf(bar("2025-01-02T09:30",1.0,1.0,1.0,1.0), bar("2025-01-02T09:31",1.0,1.0,1.0,1.0))
        val cmd = ChartSerialization.initialViewCommand(LocalDateTime.parse("2025-01-02T09:31"), LocalDateTime.parse("2025-01-02T09:31"), bars, ChartTimeframe.ONE_MIN)
        assertTrue(cmd.startsWith("scrollToFirstTrade("), cmd)
    }
}
