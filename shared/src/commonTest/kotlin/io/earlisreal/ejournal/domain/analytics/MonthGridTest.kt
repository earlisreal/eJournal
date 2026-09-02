package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthGridTest {

    private fun assertCompleteMondayFirst(cells: List<LocalDate>) {
        assertEquals(0, cells.size % 7)
        assertEquals(1, cells.first().dayOfWeek.isoDayNumber)
        assertEquals(7, cells.last().dayOfWeek.isoDayNumber)
        assertEquals(cells.size - 1, cells.zipWithNext().count { (a, b) -> b == a.plus(1, DateTimeUnit.DAY) })
    }

    @Test
    fun mondayFirstGridIncludesLeadingAndTrailingAdjacentDates() {
        // 2024-09-01 is a Sunday, so the grid starts on the prior Monday.
        val cells = monthGrid(2024, 9)
        assertCompleteMondayFirst(cells)
        assertEquals(LocalDate(2024, 8, 26), cells.first())
        assertEquals(LocalDate(2024, 10, 6), cells.last())
        assertEquals(30, cells.count { it.monthNumber == 9 })
    }

    @Test
    fun midWeekStartHasCorrectAdjacentDates() {
        // 2024-06-01 is a Saturday, so the first row starts on 2024-05-27.
        val cells = monthGrid(2024, 6)
        assertCompleteMondayFirst(cells)
        assertEquals(LocalDate(2024, 5, 27), cells.first())
        assertEquals(LocalDate(2024, 6, 30), cells.last())
        assertEquals(30, cells.count { it.monthNumber == 6 })
    }

    @Test
    fun leapFebruaryHas29Days() {
        // 2024-02-01 is a Thursday -> 2024-01-29 through 2024-03-03.
        val cells = monthGrid(2024, 2)
        assertCompleteMondayFirst(cells)
        assertEquals(LocalDate(2024, 1, 29), cells.first())
        assertEquals(LocalDate(2024, 3, 3), cells.last())
        assertEquals(29, cells.count { it.monthNumber == 2 })
    }

    @Test
    fun nonLeapFebruaryHas28Days() {
        // 2023-02-01 is a Wednesday -> 2023-01-30 through 2023-03-05.
        val cells = monthGrid(2023, 2)
        assertCompleteMondayFirst(cells)
        assertEquals(LocalDate(2023, 1, 30), cells.first())
        assertEquals(LocalDate(2023, 3, 5), cells.last())
        assertEquals(28, cells.count { it.monthNumber == 2 })
    }

    @Test
    fun finalWeekCanCrossIntoNextYear() {
        val cells = monthGrid(2024, 12)

        assertCompleteMondayFirst(cells)
        assertEquals(LocalDate(2024, 11, 25), cells.first())
        assertEquals(LocalDate(2025, 1, 5), cells.last())
        assertEquals(LocalDate(2024, 12, 30), cells[cells.lastIndex - 6])
    }
}
