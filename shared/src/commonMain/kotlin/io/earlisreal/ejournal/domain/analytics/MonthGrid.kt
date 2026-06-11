package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Calendar cells for a month, Sunday-first: leading nulls for the blanks before the 1st, then one
 * LocalDate per day of the month. The UI wraps every 7 cells, so no trailing fill is added.
 */
fun monthGrid(year: Int, month: Int): List<LocalDate?> {
    val first = LocalDate(year, month, 1)
    val leadingBlanks = first.dayOfWeek.isoDayNumber % 7 // Sunday-first: Sun=0, Mon=1, ... Sat=6
    val daysInMonth = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    val cells = ArrayList<LocalDate?>(leadingBlanks + daysInMonth)
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..daysInMonth) cells.add(LocalDate(year, month, day))
    return cells
}
