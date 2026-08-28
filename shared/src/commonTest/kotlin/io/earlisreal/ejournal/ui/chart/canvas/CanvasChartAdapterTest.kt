package io.earlisreal.ejournal.ui.chart.canvas

import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasChartAdapterTest {

    @Test
    fun `marker is omitted when the transaction falls across a missing bar gap`() {
        val position = ClosedPosition(
            symbol = "AAPL",
            entryDatetime = LocalDateTime.parse("2026-06-10T09:30:05"),
            exitDatetime = LocalDateTime.parse("2026-06-10T09:31:05"),
            averageEntryPrice = 100.0,
            averageExitPrice = 101.0,
            shares = 1.0,
            fees = 0.0,
            profitLoss = 1.0,
            transactions = listOf(
                Transaction(1L, 1L, "AAPL", LocalDateTime.parse("2026-06-10T09:31:05"), Action.SELL, 101.0, 1.0, 0.0),
            ),
        )
        val bars = listOf(
            Bar("AAPL", Timeframe.TEN_SECONDS, LocalDateTime.parse("2026-06-10T09:30:00"), 100.0, 101.0, 99.0, 100.5, 10L),
        )

        assertEquals(emptyList(), markersFor(position, bars, ChartTimeframe.TEN_SEC))
    }
}
