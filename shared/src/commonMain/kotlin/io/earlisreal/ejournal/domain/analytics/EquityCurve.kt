package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDateTime

/** One point on the cumulative-P&L curve: the running total realized at a position's exit. */
data class EquityPoint(
    val datetime: LocalDateTime,
    val cumulative: Double,
)

/** Cumulative realized P&L over time, ordered by exit datetime. Empty input → empty curve. */
fun equityCurve(positions: List<ClosedPosition>): List<EquityPoint> {
    var running = 0.0
    return positions.sortedBy { it.exitDatetime }.map { p ->
        running += p.profitLoss
        EquityPoint(p.exitDatetime, running)
    }
}
