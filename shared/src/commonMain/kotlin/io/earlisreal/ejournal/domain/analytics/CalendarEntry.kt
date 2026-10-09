package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDateTime

/** A Position's realized exits on one date; Analysis and metadata still belong to [position]. */
data class CalendarEntry(
    val position: ClosedPosition,
    val exitDatetime: LocalDateTime,
    val shares: Double,
    val profitLoss: Double,
) {
    val date get() = exitDatetime.date
}

fun calendarEntries(positions: List<ClosedPosition>): List<CalendarEntry> =
    positions.flatMap { position ->
        if (position.transactions.isEmpty()) {
            // Transaction-free fixtures/previews carry only the aggregate Position values.
            listOf(CalendarEntry(position, position.exitDatetime, position.shares, position.profitLoss))
        } else {
            position.exits.groupBy { position.transactions[it.transactionIndex].datetime.date }
                .values.map { exits ->
                    CalendarEntry(
                        position = position,
                        exitDatetime = exits.minOf { position.transactions[it.transactionIndex].datetime },
                        shares = exits.sumOf { it.shares },
                        profitLoss = exits.sumOf { it.profitLoss },
                    )
                }
        }
    }.sortedBy { it.exitDatetime }

fun calendarAnalysisPositions(entries: List<CalendarEntry>): List<ClosedPosition> =
    entries.map { it.position }.distinct()
