package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition

enum class SortColumn { SYMBOL, TYPE, ENTRY, EXIT, HELD, SHARES, AVG_ENTRY, AVG_EXIT, AVG_DIFFERENCE, FEES, PNL, PNL_PCT }
enum class SortDirection { ASC, DESC }

private fun pnlPct(p: ClosedPosition): Double {
    val cost = p.averageEntryPrice * p.shares
    return if (cost == 0.0) 0.0 else p.profitLoss / cost
}

fun sortPositions(
    positions: List<ClosedPosition>,
    column: SortColumn,
    direction: SortDirection,
): List<ClosedPosition> {
    if (column == SortColumn.HELD) return positions.sortedWith { a, b ->
        val x = a.verifiedHoldSeconds()
        val y = b.verifiedHoldSeconds()
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            direction == SortDirection.ASC -> x.compareTo(y)
            else -> y.compareTo(x)
        }
    }
    val comparator: Comparator<ClosedPosition> = when (column) {
        SortColumn.SYMBOL -> compareBy { it.symbol }
        SortColumn.TYPE -> compareBy { classifyTradeType(it) }
        SortColumn.ENTRY -> compareBy { it.entryDatetime }
        SortColumn.EXIT -> compareBy { it.exitDatetime }
        SortColumn.HELD -> compareBy { it.verifiedHoldSeconds() }
        SortColumn.SHARES -> compareBy { it.shares }
        SortColumn.AVG_ENTRY -> compareBy { it.averageEntryPrice }
        SortColumn.AVG_EXIT -> compareBy { it.averageExitPrice }
        SortColumn.AVG_DIFFERENCE -> compareBy { it.averagePriceDifference }
        SortColumn.FEES -> compareBy { it.fees }
        SortColumn.PNL -> compareBy { it.profitLoss }
        SortColumn.PNL_PCT -> compareBy { pnlPct(it) }
    }
    return if (direction == SortDirection.ASC) positions.sortedWith(comparator)
    else positions.sortedWith(comparator.reversed())
}
