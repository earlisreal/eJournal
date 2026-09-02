package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Calendar cells for a month as complete Monday-through-Sunday weeks, including adjacent-month
 * dates at both edges.
 */
fun monthGrid(year: Int, month: Int): List<LocalDate> {
    val first = LocalDate(year, month, 1)
    val start = first.minus(first.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val end = last.plus(7 - last.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    val daysInMonth = last.dayOfMonth
    val cells = ArrayList<LocalDate>(daysInMonth + 6)
    var date = start
    while (date <= end) {
        cells.add(date)
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return cells
}
