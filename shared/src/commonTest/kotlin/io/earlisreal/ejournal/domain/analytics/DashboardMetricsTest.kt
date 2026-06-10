package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardMetricsTest {

    private fun pos(pnl: Double) = ClosedPosition(
        symbol = "X",
        entryDatetime = LocalDateTime.parse("2024-03-01T09:00"),
        exitDatetime = LocalDateTime.parse("2024-03-01T15:00"),
        averageEntryPrice = 10.0, averageExitPrice = 10.0,
        shares = 100.0, fees = 0.0, profitLoss = pnl,
    )

    @Test
    fun emptyListYieldsZeroSumsAndNullRatios() {
        val m = computeMetrics(emptyList())
        assertEquals(0.0, m.netPnl); assertEquals(0, m.tradeCount)
        assertNull(m.winRate); assertNull(m.profitFactor); assertNull(m.expectancy)
        assertNull(m.avgWin); assertNull(m.avgLoss); assertNull(m.largestWin); assertNull(m.largestLoss)
    }

    @Test
    fun mixedTradesComputeCorrectly() {
        val m = computeMetrics(listOf(pos(100.0), pos(-40.0), pos(60.0), pos(-20.0)))
        assertEquals(100.0, m.netPnl)
        assertEquals(160.0, m.grossProfit)
        assertEquals(-60.0, m.grossLoss)
        assertEquals(0.5, m.winRate)
        assertEquals(160.0 / 60.0, m.profitFactor!!, 1e-9)
        assertEquals(80.0, m.avgWin)
        assertEquals(-30.0, m.avgLoss)
        assertEquals(25.0, m.expectancy)
        assertEquals(100.0, m.largestWin)
        assertEquals(-40.0, m.largestLoss)
        assertEquals(4, m.tradeCount)
    }

    @Test
    fun allWinsGivesInfiniteProfitFactor() {
        val m = computeMetrics(listOf(pos(50.0), pos(70.0)))
        assertTrue(m.profitFactor!!.isInfinite())
        assertNull(m.avgLoss); assertNull(m.largestLoss)
    }
}
