package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.LocalDate

data class DaySummary(val date: LocalDate, val netPnl: Double, val tradeCount: Int)

/** Summarizes realized exits, counting each Position once per exit date. */
fun dailySummaries(entries: List<CalendarEntry>): Map<LocalDate, DaySummary> =
    entries.groupBy { it.date }
        .mapValues { (date, dayEntries) ->
            DaySummary(
                date = date,
                netPnl = dayEntries.sumOf { it.profitLoss },
                tradeCount = dayEntries.size,
            )
        }
