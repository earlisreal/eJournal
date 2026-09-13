package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs

data class DashboardMetrics(
    val netPnl: Double,
    val grossProfit: Double,
    val grossLoss: Double,
    val winRate: Double?,
    val profitFactor: Double?,
    val avgWin: Double?,
    val avgLoss: Double?,
    val avgPriceDifference: Double?,
    val expectancy: Double?,
    val largestWin: Double?,
    val largestLoss: Double?,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val scratchCount: Int,
    val breakEvenCount: Int,
    /** Reward-to-risk: avgWin / |avgLoss|. Displayed as "1 : payoffRatio". Null without both winners and losers. */
    val payoffRatio: Double?,
    val maxWinStreak: Int,
    val maxLossStreak: Int,
    /** Mean holding time (exit − entry) in seconds across all positions; null when empty. */
    val avgHoldSeconds: Double?,
)

fun computeMetrics(positions: List<ClosedPosition>): DashboardMetrics {
    val n = positions.size
    val pnls = positions.map { it.profitLoss }
    val financialWins = pnls.filter { it > 0.0 }
    val financialLosses = pnls.filter { it < 0.0 }
    val winners = positions.filter { !it.isScratch && it.profitLoss > 0.0 }.map { it.profitLoss }
    val losers = positions.filter { !it.isScratch && it.profitLoss < 0.0 }.map { it.profitLoss }
    val grossProfit = financialWins.sum()
    val grossLoss = financialLosses.sum()
    val avgWin = if (winners.isEmpty()) null else winners.sum() / winners.size
    val avgLoss = if (losers.isEmpty()) null else losers.sum() / losers.size
    val scratchCount = positions.count { it.isScratch }
    val breakEvenCount = positions.count { !it.isScratch && it.profitLoss == 0.0 }
    val decidedCount = winners.size + losers.size

    val (maxWinStreak, maxLossStreak) = computeStreaks(positions)

    return DashboardMetrics(
        netPnl = pnls.sum(),
        grossProfit = grossProfit,
        grossLoss = grossLoss,
        winRate = if (decidedCount == 0) null else winners.size.toDouble() / decidedCount,
        profitFactor = when {
            n == 0 -> null
            grossLoss == 0.0 -> if (grossProfit > 0.0) Double.POSITIVE_INFINITY else null
            else -> grossProfit / abs(grossLoss)
        },
        avgWin = avgWin,
        avgLoss = avgLoss,
        avgPriceDifference = if (n == 0) null else positions.sumOf { it.averagePriceDifference } / n,
        expectancy = if (n == 0) null else pnls.sum() / n,
        largestWin = winners.maxOrNull(),
        largestLoss = losers.minOrNull(),
        tradeCount = n,
        winCount = winners.size,
        lossCount = losers.size,
        scratchCount = scratchCount,
        breakEvenCount = breakEvenCount,
        payoffRatio = if (avgWin == null || avgLoss == null) null else avgWin / abs(avgLoss),
        maxWinStreak = maxWinStreak,
        maxLossStreak = maxLossStreak,
        avgHoldSeconds = if (n == 0) null else positions.sumOf { holdSeconds(it) }.toDouble() / n,
    )
}

/** Longest run of consecutive winners and losers, ordered by exit time. Scratch and break-even Positions reset both runs. */
private fun computeStreaks(positions: List<ClosedPosition>): Pair<Int, Int> {
    var maxWin = 0; var maxLoss = 0
    var curWin = 0; var curLoss = 0
    for (p in positions.sortedBy { it.exitDatetime }) {
        when {
            p.isScratch -> { curWin = 0; curLoss = 0 }
            p.profitLoss > 0.0 -> { curWin++; curLoss = 0; if (curWin > maxWin) maxWin = curWin }
            p.profitLoss < 0.0 -> { curLoss++; curWin = 0; if (curLoss > maxLoss) maxLoss = curLoss }
            else -> { curWin = 0; curLoss = 0 }
        }
    }
    return maxWin to maxLoss
}

private fun holdSeconds(p: ClosedPosition): Long =
    p.exitDatetime.toInstant(TimeZone.UTC).epochSeconds - p.entryDatetime.toInstant(TimeZone.UTC).epochSeconds
