package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlin.math.abs

data class DashboardMetrics(
    val netPnl: Double,
    val grossProfit: Double,
    val grossLoss: Double,
    val winRate: Double?,
    val profitFactor: Double?,
    val avgWin: Double?,
    val avgLoss: Double?,
    val expectancy: Double?,
    val largestWin: Double?,
    val largestLoss: Double?,
    val tradeCount: Int,
)

fun computeMetrics(positions: List<ClosedPosition>): DashboardMetrics {
    val n = positions.size
    val pnls = positions.map { it.profitLoss }
    val winners = pnls.filter { it > 0.0 }
    val losers = pnls.filter { it < 0.0 }
    val grossProfit = winners.sum()
    val grossLoss = losers.sum()

    return DashboardMetrics(
        netPnl = pnls.sum(),
        grossProfit = grossProfit,
        grossLoss = grossLoss,
        winRate = if (n == 0) null else winners.size.toDouble() / n,
        profitFactor = when {
            n == 0 -> null
            grossLoss == 0.0 -> if (grossProfit > 0.0) Double.POSITIVE_INFINITY else null
            else -> grossProfit / abs(grossLoss)
        },
        avgWin = if (winners.isEmpty()) null else grossProfit / winners.size,
        avgLoss = if (losers.isEmpty()) null else grossLoss / losers.size,
        expectancy = if (n == 0) null else pnls.sum() / n,
        largestWin = winners.maxOrNull(),
        largestLoss = losers.minOrNull(),
        tradeCount = n,
    )
}
