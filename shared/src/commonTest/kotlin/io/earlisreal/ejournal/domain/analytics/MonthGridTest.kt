package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthGridTest {

    private fun leadingBlanks(cells: List<LocalDate?>) = cells.takeWhile { it == null }.size
    private fun days(cells: List<LocalDate?>) = cells.filterNotNull()

    @Test
    fun monthStartingOnSundayHasNoLeadingBlanks() {
        // 2024-09-01 is a Sunday.
        val cells = monthGrid(2024, 9)
        assertEquals(0, leadingBlanks(cells))
        assertEquals(30, days(cells).size)
        assertEquals(LocalDate(2024, 9, 1), days(cells).first())
    }

    @Test
    fun midWeekStartHasCorrectLeadingBlanks() {
        // 2024-06-01 is a Saturday -> 6 leading blanks (Sun..Fri), then the 1st in the Sat column.
        val cells = monthGrid(2024, 6)
        assertEquals(6, leadingBlanks(cells))
        assertEquals(30, days(cells).size)
    }

    @Test
    fun thirtyOneDayMonth() {
        // 2024-07-01 is a Monday -> 1 leading blank.
        val cells = monthGrid(2024, 7)
        assertEquals(1, leadingBlanks(cells))
        assertEquals(31, days(cells).size)
        assertEquals(LocalDate(2024, 7, 31), days(cells).last())
    }

    @Test
    fun leapFebruaryHas29Days() {
        // 2024-02-01 is a Thursday -> 4 leading blanks.
        val cells = monthGrid(2024, 2)
        assertEquals(4, leadingBlanks(cells))
        assertEquals(29, days(cells).size)
    }

    @Test
    fun nonLeapFebruaryHas28Days() {
        // 2023-02-01 is a Wednesday -> 3 leading blanks.
        val cells = monthGrid(2023, 2)
        assertEquals(3, leadingBlanks(cells))
        assertEquals(28, days(cells).size)
    }
}
