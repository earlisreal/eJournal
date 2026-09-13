package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.TradeDirection
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardMetricsTest {

    private fun position(
        pnl: Double,
        exit: String = "2024-03-01T15:00",
        entry: String = "2024-03-01T09:00",
        averageEntryPrice: Double = 10.0,
        averageExitPrice: Double = if (pnl < 0.0) 9.0 else 11.0,
        fees: Double = 0.0,
        direction: TradeDirection = TradeDirection.LONG,
    ) = ClosedPosition(
        symbol = "X",
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = averageEntryPrice, averageExitPrice = averageExitPrice,
        shares = 100.0, fees = fees, profitLoss = pnl, direction = direction,
    )

    private fun pos(pnl: Double) = position(pnl)

    /** Position with a custom exit timestamp (entry defaults to the same), for ordering-sensitive metrics. */
    private fun posAt(pnl: Double, exit: String, entry: String = exit) = position(pnl, exit, entry)

    private fun scratch(
        pnl: Double = 0.0,
        exit: String = "2024-03-01T15:00",
        entry: String = "2024-03-01T09:00",
        fees: Double = 0.0,
        direction: TradeDirection = TradeDirection.LONG,
    ) = position(pnl, exit, entry, averageExitPrice = 10.0, fees = fees, direction = direction)

    @Test
    fun emptyListYieldsZeroSumsAndNullRatios() {
        val m = computeMetrics(emptyList())
        assertEquals(0.0, m.netPnl); assertEquals(0, m.tradeCount)
        assertNull(m.winRate); assertNull(m.profitFactor); assertNull(m.expectancy)
        assertNull(m.avgWin); assertNull(m.avgLoss); assertNull(m.largestWin); assertNull(m.largestLoss)
        assertEquals(0, m.winCount); assertEquals(0, m.lossCount); assertEquals(0, m.scratchCount); assertEquals(0, m.breakEvenCount)
        assertNull(m.payoffRatio); assertNull(m.avgHoldSeconds)
        assertEquals(0, m.maxWinStreak); assertEquals(0, m.maxLossStreak)
    }

    @Test
    fun feeDebitScratchIsNotLossOrBreakEven() {
        val m = computeMetrics(listOf(scratch(pnl = -2.0, fees = 2.0)))

        assertEquals(1, m.scratchCount)
        assertEquals(0, m.lossCount)
        assertEquals(0, m.breakEvenCount)
        assertNull(m.winRate)
    }

    @Test
    fun zeroFeeFlatPositionIsScratch() {
        val m = computeMetrics(listOf(scratch()))

        assertEquals(1, m.scratchCount)
        assertEquals(0, m.breakEvenCount)
    }

    @Test
    fun nonFlatZeroPnlPositionIsBreakEven() {
        val m = computeMetrics(listOf(position(pnl = 0.0, averageExitPrice = 10.02, fees = 2.0)))

        assertEquals(0, m.scratchCount)
        assertEquals(1, m.breakEvenCount)
    }

    @Test
    fun anyNonzeroPriceDifferenceIsNotScratch() {
        val m = computeMetrics(listOf(position(pnl = 1.0, averageExitPrice = 10.0000000001)))

        assertEquals(0, m.scratchCount)
        assertEquals(1, m.winCount)
    }

    @Test
    fun flatLongAndShortPositionsAreBothScratch() {
        val m = computeMetrics(
            listOf(
                scratch(direction = TradeDirection.LONG),
                scratch(direction = TradeDirection.SHORT),
            )
        )

        assertEquals(2, m.scratchCount)
        assertEquals(0, m.winCount)
        assertEquals(0, m.lossCount)
        assertEquals(0, m.breakEvenCount)
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
        val m = computeMetrics(listOf(pos(100.0), pos(-40.0), scratch(-2.0), pos(0.0), pos(60.0)))
        assertEquals(2, m.winCount)
        assertEquals(1, m.lossCount)
        assertEquals(1, m.scratchCount)
        assertEquals(1, m.breakEvenCount)
        assertEquals(m.tradeCount, m.winCount + m.lossCount + m.scratchCount + m.breakEvenCount)
        assertEquals(2.0 / 3.0, m.winRate!!, 1e-9)
    }

    @Test
    fun scratchFeesStayFinancialButNotClassifiedLossMetrics() {
        val m = computeMetrics(listOf(pos(100.0), pos(-40.0), scratch(-2.0, fees = 2.0)))

        assertEquals(58.0, m.netPnl)
        assertEquals(100.0, m.grossProfit)
        assertEquals(-42.0, m.grossLoss)
        assertEquals(100.0 / 42.0, m.profitFactor!!, 1e-9)
        assertEquals(58.0 / 3.0, m.expectancy!!, 1e-9)
        assertEquals(-40.0, m.avgLoss)
        assertEquals(-40.0, m.largestLoss)
        assertEquals(100.0 / 40.0, m.payoffRatio!!, 1e-9)
    }

    @Test
    fun scratchFeesDoNotCreateAWinLossDenominator() {
        val m = computeMetrics(listOf(pos(100.0), scratch(-2.0, fees = 2.0)))

        assertEquals(1.0, m.winRate)
        assertNull(m.avgLoss)
        assertNull(m.largestLoss)
        assertNull(m.payoffRatio)
        assertEquals(50.0, m.profitFactor)
    }

    @Test
    fun allScratchesHaveNoWinLossMetricsOrStreaks() {
        val m = computeMetrics(
            listOf(
                scratch(-2.0, fees = 2.0),
                scratch(),
            )
        )

        assertEquals(2, m.scratchCount)
        assertNull(m.winRate)
        assertEquals(0, m.maxWinStreak)
        assertEquals(0, m.maxLossStreak)
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
    fun scratchResetsBothStreaks() {
        val winStreak = computeMetrics(
            listOf(
                posAt(10.0, "2024-03-01T10:00"),
                posAt(10.0, "2024-03-02T10:00"),
                scratch(-2.0, "2024-03-03T10:00"),
                posAt(10.0, "2024-03-04T10:00"),
            )
        )
        val lossStreak = computeMetrics(
            listOf(
                posAt(-10.0, "2024-03-01T10:00"),
                posAt(-10.0, "2024-03-02T10:00"),
                scratch(-2.0, "2024-03-03T10:00"),
                posAt(-10.0, "2024-03-04T10:00"),
            )
        )

        assertEquals(2, winStreak.maxWinStreak)
        assertEquals(2, lossStreak.maxLossStreak)
    }

    @Test
    fun avgHoldSecondsIsMeanDuration() {
        // Each pos() runs 09:00 -> 15:00 = 6h = 21600s
        val m = computeMetrics(listOf(pos(10.0), pos(-10.0)))
        assertEquals(21600.0, m.avgHoldSeconds!!, 1e-9)
    }
}
