package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

enum class SortColumn { SYMBOL, TYPE, ENTRY, EXIT, HELD, SHARES, AVG_ENTRY, AVG_EXIT, FEES, PNL, PNL_PCT }
enum class SortDirection { ASC, DESC }

private fun heldSeconds(p: ClosedPosition): Long =
    p.exitDatetime.toInstant(TimeZone.UTC).epochSeconds - p.entryDatetime.toInstant(TimeZone.UTC).epochSeconds

private fun pnlPct(p: ClosedPosition): Double {
    val cost = p.averageEntryPrice * p.shares
    return if (cost == 0.0) 0.0 else p.profitLoss / cost
}

fun sortPositions(
    positions: List<ClosedPosition>,
    column: SortColumn,
    direction: SortDirection,
): List<ClosedPosition> {
    val comparator: Comparator<ClosedPosition> = when (column) {
        SortColumn.SYMBOL -> compareBy { it.symbol }
        SortColumn.TYPE -> compareBy { classifyTradeType(it) }
        SortColumn.ENTRY -> compareBy { it.entryDatetime }
        SortColumn.EXIT -> compareBy { it.exitDatetime }
        SortColumn.HELD -> compareBy { heldSeconds(it) }
        SortColumn.SHARES -> compareBy { it.shares }
        SortColumn.AVG_ENTRY -> compareBy { it.averageEntryPrice }
        SortColumn.AVG_EXIT -> compareBy { it.averageExitPrice }
        SortColumn.FEES -> compareBy { it.fees }
        SortColumn.PNL -> compareBy { it.profitLoss }
        SortColumn.PNL_PCT -> compareBy { pnlPct(it) }
    }
    return if (direction == SortDirection.ASC) positions.sortedWith(comparator)
    else positions.sortedWith(comparator.reversed())
}
