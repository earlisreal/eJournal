package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateRangeTest {

    // 2024-06-12 is a Wednesday, Q2.
    private val today = LocalDate(2024, 6, 12)

    @Test
    fun thisWeekStartsMonday() {
        val r = resolveRange(DateRangePreset.THIS_WEEK, today)
        assertEquals(LocalDate(2024, 6, 10), r.from) // Monday
        assertEquals(today, r.to)
    }

    @Test
    fun thisMonthStartsFirst() {
        assertEquals(DateRange(LocalDate(2024, 6, 1), today), resolveRange(DateRangePreset.THIS_MONTH, today))
    }

    @Test
    fun thisQuarterStartsAtQuarterMonth() {
        assertEquals(DateRange(LocalDate(2024, 4, 1), today), resolveRange(DateRangePreset.THIS_QUARTER, today))
    }

    @Test
    fun ytdStartsJan1() {
        assertEquals(DateRange(LocalDate(2024, 1, 1), today), resolveRange(DateRangePreset.YTD, today))
    }

    @Test
    fun lastYearIsFullPreviousYear() {
        assertEquals(DateRange(LocalDate(2023, 1, 1), LocalDate(2023, 12, 31)), resolveRange(DateRangePreset.LAST_YEAR, today))
    }

    @Test
    fun allTimeIsUnbounded() {
        val r = resolveRange(DateRangePreset.ALL_TIME, today)
        assertNull(r.from); assertNull(r.to)
    }

    @Test
    fun customReturnsSuppliedRange() {
        val custom = DateRange(LocalDate(2024, 1, 5), LocalDate(2024, 2, 6))
        assertEquals(custom, resolveRange(DateRangePreset.CUSTOM, today, custom))
    }
}
