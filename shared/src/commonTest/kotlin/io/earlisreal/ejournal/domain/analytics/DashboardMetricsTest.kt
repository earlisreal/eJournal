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

    /** Position with a custom exit timestamp (entry defaults to the same), for ordering-sensitive metrics. */
    private fun posAt(pnl: Double, exit: String, entry: String = exit) = ClosedPosition(
        symbol = "X",
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 10.0, averageExitPrice = 10.0,
        shares = 100.0, fees = 0.0, profitLoss = pnl,
    )

    @Test
    fun emptyListYieldsZeroSumsAndNullRatios() {
        val m = computeMetrics(emptyList())
        assertEquals(0.0, m.netPnl); assertEquals(0, m.tradeCount)
        assertNull(m.winRate); assertNull(m.profitFactor); assertNull(m.expectancy)
        assertNull(m.avgWin); assertNull(m.avgLoss); assertNull(m.largestWin); assertNull(m.largestLoss)
        assertEquals(0, m.winCount); assertEquals(0, m.lossCount); assertEquals(0, m.breakEvenCount)
        assertNull(m.payoffRatio); assertNull(m.avgHoldSeconds)
        assertEquals(0, m.maxWinStreak); assertEquals(0, m.maxLossStreak)
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
        assertNull(m.payoffRatio)
    }

    @Test
    fun countsWinnersLosersAndBreakEvens() {
        val m = computeMetrics(listOf(pos(100.0), pos(-40.0), pos(0.0), pos(60.0)))
        assertEquals(2, m.winCount)
        assertEquals(1, m.lossCount)
        assertEquals(1, m.breakEvenCount)
    }

    @Test
    fun payoffRatioIsAvgWinOverAbsAvgLoss() {
        // avgWin = (100+60)/2 = 80 ; avgLoss = (-40-20)/2 = -30 ; payoff = 80 / 30
        val m = computeMetrics(listOf(pos(100.0), pos(-40.0), pos(60.0), pos(-20.0)))
        assertEquals(80.0 / 30.0, m.payoffRatio!!, 1e-9)
    }

    @Test
    fun payoffRatioNullWithoutBothWinnersAndLosers() {
        assertNull(computeMetrics(listOf(pos(10.0), pos(20.0))).payoffRatio)
        assertNull(computeMetrics(listOf(pos(-10.0), pos(-20.0))).payoffRatio)
    }

    @Test
    fun streaksCountLongestConsecutiveRunsByExitOrderRegardlessOfInputOrder() {
        // Chronological by exit: W W L W L L L  ->  maxWin = 2, maxLoss = 3
        val trades = listOf(
            posAt(-10.0, "2024-03-07T10:00"), // out of order on purpose
            posAt(50.0, "2024-03-01T10:00"),
            posAt(-10.0, "2024-03-06T10:00"),
            posAt(50.0, "2024-03-02T10:00"),
            posAt(20.0, "2024-03-04T10:00"),
            posAt(-10.0, "2024-03-03T10:00"),
            posAt(-10.0, "2024-03-05T10:00"),
        )
        val m = computeMetrics(trades)
        assertEquals(2, m.maxWinStreak)
        assertEquals(3, m.maxLossStreak)
    }

    @Test
    fun breakEvenResetsBothStreaks() {
        // W W BE W  ->  maxWin = 2 (the break-even breaks the run)
        val trades = listOf(
            posAt(10.0, "2024-03-01T10:00"),
            posAt(10.0, "2024-03-02T10:00"),
            posAt(0.0, "2024-03-03T10:00"),
            posAt(10.0, "2024-03-04T10:00"),
        )
        val m = computeMetrics(trades)
        assertEquals(2, m.maxWinStreak)
        assertEquals(0, m.maxLossStreak)
    }

    @Test
    fun avgHoldSecondsIsMeanDuration() {
        // Each pos() runs 09:00 -> 15:00 = 6h = 21600s
        val m = computeMetrics(listOf(pos(10.0), pos(-10.0)))
        assertEquals(21600.0, m.avgHoldSeconds!!, 1e-9)
    }
}
