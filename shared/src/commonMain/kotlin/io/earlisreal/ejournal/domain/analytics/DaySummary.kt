package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDate

data class DaySummary(val date: LocalDate, val netPnl: Double, val tradeCount: Int)

/** Groups closed positions by their exit date, summing P&L and counting trades per day. */
fun dailySummaries(positions: List<ClosedPosition>): Map<LocalDate, DaySummary> =
    positions.groupBy { it.exitDatetime.date }
        .mapValues { (date, dayPositions) ->
            DaySummary(
                date = date,
                netPnl = dayPositions.sumOf { it.profitLoss },
                tradeCount = dayPositions.size,
            )
        }
